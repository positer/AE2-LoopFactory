param(
    [Parameter(Mandatory=$true)][ValidateSet('1.21.1','26.1.2')][string]$Generation,
    [Parameter(Mandatory=$true)][string]$EvidencePrefix
)
$ErrorActionPreference = 'Stop'
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$evidenceRoot = [IO.Path]::GetFullPath((Join-Path $taskRoot 'archive/2026-09-08-loop-factory-0.0.5'))
if ($EvidencePrefix -notmatch '^[A-Za-z0-9_.-]+$') { throw 'Invalid evidence prefix' }
if(Test-Path -LiteralPath (Join-Path $evidenceRoot "$EvidencePrefix-summary.json")){throw 'Refusing to overwrite restart summary'}
$helper = Join-Path $PSScriptRoot "$Generation/build/libs/ae2lf-runtime-probe-mc$Generation-1.jar"
if (-not (Test-Path -LiteralPath $helper)) { throw 'Build the generation-specific helper first' }
$runMods = Join-Path $taskRoot "versions/neoforge-$Generation/run/mods"
New-Item -ItemType Directory -Force -Path $runMods | Out-Null
Copy-Item -LiteralPath $helper -Destination (Join-Path $runMods "ae2lf-runtime-probe-mc$Generation-1.jar")
$dependencyPath="C:/Users/12252/Desktop/Files/Minecraft/PCL/.minecraft/versions/AE2-lightoptimizer-$Generation/mods"
Get-ChildItem -LiteralPath $dependencyPath | Where-Object { $_.Name -like 'AppliedFlux*' -or $_.Name -like 'Glodium*' } | Copy-Item -Destination $runMods
$originalOptions = $env:JAVA_TOOL_OPTIONS
$summary = [ordered]@{generation=$Generation; status='running'}
$pairProductionHash=$null
$pairHelperHash=(Get-FileHash -LiteralPath $helper -Algorithm SHA256).Hash.ToLower()
try {
    $world = $null
    foreach ($stage in @('prepare','resume')) {
        $evidence = Join-Path $evidenceRoot "$EvidencePrefix-$stage"
        if (Test-Path -LiteralPath $evidence) { throw "Refusing to overwrite evidence: $evidence" }
        New-Item -ItemType Directory -Path $evidence | Out-Null
        $productionJar=Join-Path $taskRoot "versions/neoforge-$Generation/build/libs/ae2lf-neoforge-mc$Generation-0.0.5.jar"
        $productionHash=(Get-FileHash -LiteralPath $productionJar -Algorithm SHA256).Hash.ToLower()
        if($null -eq $pairProductionHash){$pairProductionHash=$productionHash}
        if($productionHash -ne $pairProductionHash -or (Get-FileHash -LiteralPath $helper -Algorithm SHA256).Hash.ToLower() -ne $pairHelperHash){throw 'Production/helper identity changed between independent JVM stages'}
        @{generation=$Generation;productionSha256=$productionHash;helperSha256=(Get-FileHash -LiteralPath $helper -Algorithm SHA256).Hash.ToLower()} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $evidence 'artifact.json')
        & python (Join-Path $PSScriptRoot 'verify-runtime-identity.py') --generation $Generation --production $productionJar --report (Join-Path $evidence 'runtime-identity-before.json')
        if($LASTEXITCODE -ne 0){throw "ModDev main classes/resources differ from the frozen production JAR before $stage"}

        $agentPath=(Join-Path $PSScriptRoot 'background-agent/hidden-window-agent.jar').Replace('\','/')
        $env:JAVA_TOOL_OPTIONS = '"-javaagent:'+$agentPath+'" -Dae2lf.probe.background=true -Dae2lf.probe=true -Dmixin.debug.export=true -Dae2lf.probe.restart=' + $stage +
            ' "-Dae2lf.probe.reportDir=' + $evidence.Replace('\','/') + '"'
        if ($stage -eq 'resume') { $env:JAVA_TOOL_OPTIONS += ' -Dae2lf.probe.world=' + $world }
        Write-Output "$Generation ${stage}: launching a separate real client process"
        # Windows PowerShell wraps a native process stderr line as NativeCommandError.
        # JAVA_TOOL_OPTIONS is informational stderr; judge the native exit code and reports.
        try {
            $ErrorActionPreference = 'Continue'
            & powershell -NoProfile -File (Join-Path $taskRoot "build-$Generation.ps1") --offline --no-daemon runClient *> (Join-Path $evidence 'client.log')
            $nativeExit = $LASTEXITCODE
        } finally { $ErrorActionPreference = 'Stop' }
        @{stage=$stage;exitCode=$nativeExit;finishedAt=[DateTimeOffset]::Now.ToString('o')} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $evidence 'process-exit.json')
        $summary['lastStage']=$stage
        $summary['nativeExit']=$nativeExit
        $diagnostics=[ordered]@{}
        foreach($reportName in @('factory-report.json','restart-extras.json','native-cpu-report.json')) {
            $candidate=Join-Path $evidence $reportName
            if(Test-Path -LiteralPath $candidate){
                $data=Get-Content -Raw -LiteralPath $candidate | ConvertFrom-Json
                $failedKeys=@($data.PSObject.Properties | Where-Object {$_.Value -is [bool] -and $_.Value -eq $false} | ForEach-Object Name)
                $diagnostics[$reportName]=[ordered]@{status=$data.status;failure=$data.failure;resumeFailure=$data.resume_failure;failedAssertions=$failedKeys}
            }
        }
        $summary['lastStageReports']=$diagnostics
        if((Get-FileHash -LiteralPath $productionJar -Algorithm SHA256).Hash.ToLower() -ne $productionHash){throw 'Production artifact changed during restart gate'}
        if ($nativeExit -ne 0) { throw "Client failed during $stage; retained $evidence/client.log" }
        & python (Join-Path $PSScriptRoot 'verify-runtime-identity.py') --generation $Generation --production $productionJar --report (Join-Path $evidence 'runtime-identity-after.json')
        if($LASTEXITCODE -ne 0){throw "ModDev main classes/resources differ from the frozen production JAR after $stage"}
        $failedReports=@($diagnostics.Keys | Where-Object {$diagnostics[$_].status -eq 'failed'})
        if($failedReports.Count -gt 0){throw "Independent $stage report failed: $($failedReports -join ', '); see lastStageReports for original assertions"}
        if(Test-Path -LiteralPath (Join-Path $evidence 'shutdown-timeout.txt')){throw "Shutdown timeout during $stage"}
        $report = Get-Content -Raw -LiteralPath (Join-Path $evidence 'native-cpu-report.json') | ConvertFrom-Json
        $expected = if ($stage -eq 'prepare') {'restart_ready'} else {'passed'}
        if ($report.status -ne $expected -or -not (Test-Path (Join-Path $evidence 'normal-shutdown.txt'))) {
            throw "Missing completed $stage evidence"
        }
        $extra=Get-Content -Raw -LiteralPath (Join-Path $evidence 'restart-extras.json') | ConvertFrom-Json
        if($extra.status -ne $expected){throw "Additional persistence checks failed during $stage"}
        if ($stage -eq 'prepare') {
            $world = $report.world
            if ($world -notmatch '^AE2LF-Factory-Probe-[0-9a-f-]{36}$') { throw 'Invalid fixture world' }
            $summary['world'] = $world
            $summary['preparedContinuation'] = $report.saved_continuation
        } else { $summary['restoredDelay'] = $report.restored_delay }
        Write-Output "$Generation $stage completed with native shutdown"
    }
    $summary['status'] = 'passed'
} catch {
    $summary['status']='failed'
    $summary['failure']=$_.Exception.Message
    throw
} finally {
    $env:JAVA_TOOL_OPTIONS = $originalOptions
    $json=$summary | ConvertTo-Json -Depth 5
    [IO.File]::WriteAllText((Join-Path $evidenceRoot "$EvidencePrefix-summary.json"),$json,(New-Object Text.UTF8Encoding($false)))
    Write-Output $json
}
