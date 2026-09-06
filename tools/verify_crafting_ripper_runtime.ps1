param([switch]$SkipLaunch)
$ErrorActionPreference = 'Stop'
$workspaceRoot = Split-Path -Parent $PSScriptRoot
$javapTool = Join-Path $env:USERPROFILE '.gradle/jdks/eclipse_adoptium-25-amd64-windows.2/bin/javap.exe'
if (-not (Test-Path $javapTool)) { $javapTool = (Get-Command javap -ErrorAction Stop).Source }
$previousOptions = $env:JAVA_TOOL_OPTIONS
try {
    foreach ($generation in @('1.21.1', '26.1.2')) {
        if (-not $SkipLaunch) {
            $env:JAVA_TOOL_OPTIONS = '-Dmixin.debug.export=true -Dmixin.debug.export.filter=appeng.**'
            $task = if ($generation -eq '1.21.1') { 'runData' } else { 'runClientData' }
            & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $workspaceRoot "build-$generation.ps1") --offline --no-daemon $task
            if ($LASTEXITCODE -ne 0) { throw "Build/runtime launch failed: $generation" }
        }
        $versionRoot = Join-Path $workspaceRoot "versions/neoforge-$generation"
        $exportRoot = Join-Path $versionRoot 'run/.mixin.out/class'
        $latestSource = (Get-ChildItem (Join-Path $versionRoot 'src/main/java') -Recurse -Filter '*.java' |
                Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1).LastWriteTimeUtc
        $checks = @(
            @{File='appeng/crafting/execution/CraftingCpuLogic.class'; Keys=@('CraftingRipperExecutor.acceptsPlan','CraftingRipperExecutor.execute','ae2lightoptimizer$executeRipperChain','ae2lightoptimizer$preflightRipperChain')},
            @{File='appeng/crafting/execution/ExecutingCraftingJob.class'; Keys=@('ae2lightoptimizer$markRipped','ae2loRipped','ae2loRipperRequested')},
            @{File='appeng/crafting/execution/ExecutingCraftingJob$TaskProgress.class'; Keys=@('ae2lightoptimizer$getRemaining')},
            @{File='appeng/helpers/patternprovider/PatternProviderLogic.class'; Keys=@('ae2lightoptimizer$onPushPatternSuccess','ae2lightoptimizer$onStackReturnedToNetwork')}
        )
        foreach ($check in $checks) {
            $path = Join-Path $exportRoot $check.File
            if (-not (Test-Path -LiteralPath $path)) { throw "Missing transformed class: $path" }
            if ((Get-Item -LiteralPath $path).LastWriteTimeUtc -lt $latestSource) {
                throw "Stale transformed class: $path; launch the runtime again."
            }
            $bytecode = (& $javapTool -c -p $path) -join "`n"
            if ($LASTEXITCODE -ne 0) { throw "Cannot inspect transformed class: $path" }
            foreach ($key in $check.Keys) {
                if (-not $bytecode.Contains($key)) { throw "Missing $key in $path" }
            }
        }
        Write-Output "${generation}: fresh preflight, execution, paid-state persistence, long counter and provider-lock hooks verified."
    }
} finally {
    $env:JAVA_TOOL_OPTIONS = $previousOptions
}
