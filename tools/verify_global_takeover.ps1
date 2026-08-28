[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$workspaceRoot = Split-Path -Parent $PSScriptRoot
$javap = (Get-Command javap -ErrorAction Stop).Source
$previousJavaToolOptions = $env:JAVA_TOOL_OPTIONS
$versions = @(
    @{ Name = '1.21.1'; Directory = 'versions/neoforge-1.21.1'; Task = 'runData' },
    @{ Name = '26.1.2'; Directory = 'versions/neoforge-26.1.2'; Task = 'runClientData' }
)

try {
    $env:JAVA_TOOL_OPTIONS = '-Dmixin.debug.export=true ' +
        '-Dmixin.debug.export.filter=appeng.crafting.**'

    foreach ($version in $versions) {
        $projectDirectory = Join-Path $workspaceRoot $version.Directory
        $exportedClasses = @{
            Calculation = Join-Path $projectDirectory `
                'run/.mixin.out/class/appeng/crafting/CraftingCalculation.class'
            Plan = Join-Path $projectDirectory `
                'run/.mixin.out/class/appeng/crafting/CraftingPlan.class'
            Job = Join-Path $projectDirectory `
                'run/.mixin.out/class/appeng/crafting/execution/ExecutingCraftingJob.class'
            Cpu = Join-Path $projectDirectory `
                'run/.mixin.out/class/appeng/crafting/execution/CraftingCpuLogic.class'
            Tracker = Join-Path $projectDirectory `
                'run/.mixin.out/class/appeng/crafting/execution/ElapsedTimeTracker.class'
        }
        foreach ($exportedClass in $exportedClasses.Values) {
            if (Test-Path -LiteralPath $exportedClass) {
                Remove-Item -LiteralPath $exportedClass -Force
            }
        }

        & (Join-Path $projectDirectory 'gradlew.bat') `
            --project-dir $projectDirectory $version.Task
        if ($LASTEXITCODE -ne 0) {
            throw "Mixin launch verification failed for $($version.Name)"
        }
        foreach ($entry in $exportedClasses.GetEnumerator()) {
            if (-not (Test-Path -LiteralPath $entry.Value)) {
                throw "Mixin did not transform $($entry.Key) for $($version.Name)"
            }
        }

        $calculationBytecode = (& $javap -c -p $exportedClasses.Calculation) -join "`n"
        $requiredCalculationInstructions = @(
            'Ae2GlobalCraftingOptimizer.tryPlan',
            'OptimizationAttempt.handled',
            'CallbackInfoReturnable.setReturnValue',
            'CallbackInfoReturnable.isCancelled',
            'CallbackInfoReturnable.getReturnValue'
        )
        foreach ($instruction in $requiredCalculationInstructions) {
            if (-not $calculationBytecode.Contains($instruction)) {
                throw "Missing takeover bytecode '$instruction' for $($version.Name)"
            }
        }

        $planBytecode = (& $javap -c -p $exportedClasses.Plan) -join "`n"
        $jobBytecode = (& $javap -c -p $exportedClasses.Job) -join "`n"
        $cpuBytecode = (& $javap -c -p $exportedClasses.Cpu) -join "`n"
        $trackerBytecode = (& $javap -c -p $exportedClasses.Tracker) -join "`n"
        $executionInstructions = @(
            @{ Code = $planBytecode; Value = 'ae2lightoptimizer$setSchedule' }
            @{ Code = $jobBytecode; Value = 'CompressedBatchCursor' }
            @{ Code = $jobBytecode; Value = 'CraftingExecutionScheduleCodec.write' }
            @{ Code = $cpuBytecode; Value = 'ae2lightoptimizer$selectCurrentBatch' }
            @{ Code = $cpuBytecode; Value = 'ae2lightoptimizer$limitCurrentBatch' }
            @{ Code = $cpuBytecode; Value = 'ae2lightoptimizer$advanceCurrentBatch' }
            @{ Code = $cpuBytecode; Value = 'ae2lightoptimizer$lockRingFinalOutput' }
            @{ Code = $cpuBytecode; Value = 'ae2lightoptimizer$flushRingFinalOutput' }
            @{ Code = $cpuBytecode; Value = 'RingOutputLock.releasable' }
            @{ Code = $cpuBytecode; Value = 'ListCraftingInventory.insert' }
            @{ Code = $cpuBytecode; Value = 'CraftingExecutionScheduleCodec.read' }
            @{ Code = $trackerBytecode; Value = 'ae2lightoptimizer$decrementItems' }
        )
        foreach ($instruction in $executionInstructions) {
            if (-not $instruction.Code.Contains($instruction.Value)) {
                throw "Missing execution bytecode '$($instruction.Value)' for $($version.Name)"
            }
        }

        Write-Output "$($version.Name): calculation, cyclic output lock, and compressed execution takeover verified"
    }
} finally {
    $env:JAVA_TOOL_OPTIONS = $previousJavaToolOptions
}
