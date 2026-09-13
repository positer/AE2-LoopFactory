param(
    [Parameter(Mandatory=$true)][ValidateSet('1.21.1','26.1.2')][string]$Generation,
    [Parameter(Mandatory=$true)][ValidateSet('template','smithing','layered','core256m','catalog_audit','native_catalog','exact_components','pack_catalog','tool_components')][string]$Chain,
    [Parameter(Mandatory=$true)][ValidatePattern('^ripper-[A-Za-z0-9_.-]+$')][string]$EvidenceName,
    [switch]$PrepareOnly
)
$ErrorActionPreference = 'Stop'
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$projectRoot = Join-Path $taskRoot "versions/neoforge-$Generation"
$runRoot = Join-Path $projectRoot 'run'
$runMods = [IO.Path]::GetFullPath((Join-Path $runRoot 'mods'))
$evidenceRoot = Join-Path $taskRoot 'archive/2026-09-12-expanded-background-qa'
$evidence = [IO.Path]::GetFullPath((Join-Path $evidenceRoot $EvidenceName))
$helper = Join-Path $PSScriptRoot "$Generation/build/libs/ae2lo-runtime-probe-mc$Generation-1.jar"
$agent = Join-Path $taskRoot 'tools/runtime-factory-probe/background-agent/hidden-window-agent.jar'
$versionLine = @(Get-Content -LiteralPath (Join-Path $projectRoot 'gradle.properties') | Where-Object { $_ -match '^mod_version=' })
if ($versionLine.Count -ne 1) { throw 'Expected one mod_version in the generation properties' }
$modVersion = ($versionLine[0] -split '=',2)[1].Trim()
$production = Join-Path $projectRoot "build/libs/ae2lf-neoforge-mc$Generation-$modVersion.jar"

function Assert-WorkspacePath([string]$Path) {
    $resolved = [IO.Path]::GetFullPath($Path)
    if (-not $resolved.StartsWith($taskRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing path outside this workspace: $resolved"
    }
}
function Get-Sha256([string]$Path) { (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() }
function Write-EvidenceJson([string]$Name, $Value) {
    [IO.File]::WriteAllText((Join-Path $evidence $Name), ($Value | ConvertTo-Json -Depth 20), (New-Object Text.UTF8Encoding($false)))
}
function Get-ModSnapshot {
    if (Test-Path -LiteralPath $runMods) {
        Get-ChildItem -LiteralPath $runMods -File -Recurse | Sort-Object FullName | ForEach-Object {
            [ordered]@{name=$_.FullName.Substring($runMods.Length + 1);bytes=$_.Length;sha256=(Get-Sha256 $_.FullName)}
        }
    }
}
function Read-PassingFixture([string]$Name) {
    $report = Get-Content -Raw -LiteralPath (Join-Path $evidence $Name) | ConvertFrom-Json
    if ($report.passed -ne $true) { throw "Independent fixture did not pass: $Name" }
    return $report
}
function Assert-HelperMatchesProduction {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [IO.Compression.ZipFile]::OpenRead($helper)
    try {
        $metadata = $archive.GetEntry('META-INF/neoforge.mods.toml')
        if ($null -eq $metadata) { throw 'Ripper helper is missing its NeoForge metadata' }
        $reader = New-Object IO.StreamReader($metadata.Open(), [Text.Encoding]::UTF8)
        try { $toml = $reader.ReadToEnd() } finally { $reader.Dispose() }
        $blocks = [regex]::Matches($toml, '(?ms)^\[\[dependencies\.ae2lo_runtime_probe\]\][ \t]*\r?\n(?<body>.*?)(?=^\[\[|\z)')
        $productionDependency = @($blocks | Where-Object {
            $_.Groups['body'].Value -match '(?m)^modId\s*=\s*"ae2lightoptimizer"\s*$'
        })
        if ($productionDependency.Count -ne 1) { throw 'Expected exactly one production dependency in the actual helper JAR' }
        $range = [regex]::Match($productionDependency[0].Groups['body'].Value, '(?m)^versionRange\s*=\s*"([^"]+)"\s*$')
        $expected = '[' + $modVersion + ']'
        if (-not $range.Success -or $range.Groups[1].Value -ne $expected) {
            throw "Stale/incompatible ripper helper: expected production dependency $expected, found '$($range.Groups[1].Value)'. Rebuild ripperProbeJar before starting Java."
        }
        return [ordered]@{productionDependency=$range.Groups[1].Value;testedVersion=$modVersion}
    } finally { $archive.Dispose() }
}
function Assert-RuntimeMatchesProduction {
    # ModDev runClient loads the main source set. Verify those exact bytes against the frozen JAR.
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [IO.Compression.ZipFile]::OpenRead($production)
    $classCount = 0
    $entryCount = 0
    try {
        foreach ($entry in $archive.Entries) {
            $name = $entry.FullName
            if ($name.EndsWith('/') -or $name -eq 'META-INF/MANIFEST.MF') { continue }
            if ($name -match '(^com/example/(ae2loprobe|ae2lfprobe)/|\.jar$)') { throw "Unexpected helper/nested JAR in production: $name" }
            if ($name.EndsWith('.class')) {
                $candidate = Join-Path $projectRoot ('build/classes/java/main/' + $name)
                $classCount++
            } else { $candidate = Join-Path $projectRoot ('build/resources/main/' + $name) }
            if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) { throw "Production entry missing from the runtime source set: $name" }
            $stream = $entry.Open()
            $digest = [Security.Cryptography.SHA256]::Create()
            try { $jarHash = ([BitConverter]::ToString($digest.ComputeHash($stream))).Replace('-','').ToLowerInvariant() }
            finally { $digest.Dispose(); $stream.Dispose() }
            if ((Get-Sha256 $candidate) -ne $jarHash) { throw "Runtime differs from the frozen production JAR: $name" }
            $entryCount++
        }
        $runtimeClasses = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'build/classes/java/main') -Filter '*.class' -File -Recurse)
        if ($classCount -eq 0 -or $runtimeClasses.Count -ne $classCount) { throw 'Production/runtime class inventories differ' }
        return [ordered]@{classCount=$classCount;entryCount=$entryCount;mode='ModDev main source set, byte-matched against every production JAR entry except the generated manifest'}
    } finally { $archive.Dispose() }
}

Assert-WorkspacePath $runMods
Assert-WorkspacePath $evidence
if (Test-Path -LiteralPath $evidence) { throw "Refusing to overwrite evidence: $evidence" }
foreach ($required in @($helper,$agent,$production)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Build the isolated artifacts before launching: $required" }
}
$helperCompatibility = Assert-HelperMatchesProduction
$runtimeIdentity = Assert-RuntimeMatchesProduction
if ($PrepareOnly) {
    [ordered]@{generation=$Generation;chain=$Chain;prepared=$true;productionSha256=(Get-Sha256 $production);helperSha256=(Get-Sha256 $helper);helperCompatibility=$helperCompatibility;runtimeIdentity=$runtimeIdentity;clientStarted=$false;runModsChanged=$false} | ConvertTo-Json -Depth 5 | Write-Output
    return
}
$modsExisted = Test-Path -LiteralPath $runMods
New-Item -ItemType Directory -Force -Path $runMods | Out-Null
New-Item -ItemType Directory -Path $evidence | Out-Null
$backup = Join-Path $evidence 'run-mods-backup'
New-Item -ItemType Directory -Path $backup | Out-Null
$lockPath = Join-Path $runRoot '.ae2lo-ripper-background.lock'
$lock = $null
$oldOptions = $env:JAVA_TOOL_OPTIONS
$beforeMods = @()
$removedHelpers = New-Object System.Collections.Generic.List[object]
$staged = Join-Path $runMods ([IO.Path]::GetFileName($helper))
$stagedByThisRun = $false
$summary = [ordered]@{generation=$Generation;chain=$Chain;status='running';evidence=$evidence;startedAt=[DateTime]::UtcNow.ToString('o')}
$originalFailure = $null
try {
    $lock = [IO.File]::Open($lockPath, [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
    $beforeMods = @(Get-ModSnapshot)
    Write-EvidenceJson 'run-mods-before.json' $beforeMods
    $productionHash = Get-Sha256 $production
    $helperHash = Get-Sha256 $helper
    $world = 'AE2LO-Ripper-Probe-' + $Generation + '-' + [Guid]::NewGuid().ToString()
    $artifact = [ordered]@{generation=$Generation;version=$modVersion;productionPath=$production;productionSha256=$productionHash;helperPath=$helper;helperSha256=$helperHash;helperCompatibility=$helperCompatibility;agentPath=$agent;agentSha256=(Get-Sha256 $agent);runtimeIdentity=$runtimeIdentity}
    Write-EvidenceJson 'artifact.json' $artifact
    $summary['world'] = $world
    $summary['productionSha256'] = $productionHash
    $summary['helperSha256'] = $helperHash

    # Only conflicting test helpers are staged out. Every existing mod and .bak stays in its own instance.
    $conflicts = @(Get-ChildItem -LiteralPath $runMods -File | Where-Object {
        $_.Name -match '^ae2l[fo]-runtime-probe-.*\.jar$'
    })
    foreach ($conflict in $conflicts) {
        $saved = Join-Path $backup $conflict.Name
        Copy-Item -LiteralPath $conflict.FullName -Destination $saved
        if ((Get-Sha256 $saved) -ne (Get-Sha256 $conflict.FullName)) { throw "Helper backup differs: $($conflict.Name)" }
        Assert-WorkspacePath $conflict.FullName
        Remove-Item -LiteralPath $conflict.FullName
        $removedHelpers.Add([ordered]@{path=$conflict.FullName;backup=$saved;sha256=(Get-Sha256 $saved)})
    }
    $stagedByThisRun = $true
    Copy-Item -LiteralPath $helper -Destination $staged
    if ((Get-Sha256 $staged) -ne $helperHash) { throw 'Staged ripper helper hash differs' }
    Write-EvidenceJson 'run-mods-active.json' @(Get-ModSnapshot)
    $env:JAVA_TOOL_OPTIONS = '"-javaagent:' + $agent.Replace('\','/') + '" -Dae2lf.probe=false -Dae2lo.probe=true -Dae2lo.probe.background=true -Dae2lo.probe.autoExit=true' +
        ' -Dae2lo.probe.chain=' + $Chain + ' -Dae2lo.probe.world=' + $world +
        ' "-Dae2lo.probe.reportDir=' + $evidence.Replace('\','/') + '"'
    Write-EvidenceJson 'launch.json' ([ordered]@{generation=$Generation;chain=$Chain;world=$world;javaToolOptions=$env:JAVA_TOOL_OPTIONS;launcher=(Join-Path $taskRoot "build-$Generation.ps1");arguments=@('--offline','--no-daemon','runClient');usesPcl=$false})
    Write-EvidenceJson 'background-summary.json' $summary
    Write-Output "$Generation ${Chain}: launching a hidden native client in $world"
    # Execute inside the caller's existing console; do not create a visible PowerShell window.
    # JAVA_TOOL_OPTIONS is informational stderr in Windows PowerShell. Inspect native exit and reports.
    try {
        $ErrorActionPreference = 'Continue'
        & powershell -NoProfile -File (Join-Path $taskRoot "build-$Generation.ps1") --offline --no-daemon runClient *> (Join-Path $evidence 'client.log')
        $nativeExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = 'Stop' }
    $summary['exitCode'] = $nativeExit
    if ($nativeExit -ne 0) { throw "Native client/Gradle exited with code $nativeExit" }
    if ((Get-Sha256 $production) -ne $productionHash) { throw 'Production JAR changed during the run' }
    if ((Get-Sha256 $helper) -ne $helperHash -or (Get-Sha256 $staged) -ne $helperHash) { throw 'Ripper helper changed during the run' }
    $summary['runtimeIdentityAfter'] = Assert-RuntimeMatchesProduction

    $report = Read-PassingFixture 'report.json'
    if ($report.minecraft -ne $Generation -or $report.world -ne $world) { throw 'Report generation/world is not this run' }
    $chainNames = @{template='template_growth';smithing='template_growth_then_netherite_pickaxe_smithing';layered='logs_planks_sticks_tool_and_template_growth_then_smithing';core256m='all_ten_core_tiers_crystal_growth_and_netherite_ingots';native_catalog='native_catalog_map_extension';exact_components='exact_components';catalog_audit='catalog_audit';pack_catalog='pack_catalog';tool_components='tool_components'}
    if ($report.chain -ne $chainNames[$Chain]) { throw 'The requested chain is not the completed fixture' }
    if ($report.ripperConfiguredIdleAEPerTick -ne 5) { throw 'Ripper idle drain did not remain 5 AE/t' }
    $independentReports = @{
        exact_components=@('exact-components-report.json');native_catalog=@('native-catalog-report.json','native-continuation-roundtrip.json');
        pack_catalog=@('pack-catalog-report.json');tool_components=@('tool-components-report.json');catalog_audit=@('catalog-report.json')
    }
    if ($independentReports.ContainsKey($Chain)) {
        foreach ($file in $independentReports[$Chain]) { $null = Read-PassingFixture $file }
    }
    if ($Chain -eq 'core256m') {
        if ($report.scheduleOwner -ne 'RING_TERMINAL' -or $report.bothPlannerServicesOnline -ne $true -or
            $report.run1.passed -ne $true -or $report.run1.nativeCpuTicks -ne 1 -or $report.run1.ripperCalls -ne 1 -or
            $report.run1.isolatedExecutionAE -ne 50 -or $report.run1.sameExecutionTick -ne $true -or
            $report.run1.after.core_256m -ne 3000 -or $report.run1.after.loop_crystal -ne 1) {
            throw 'Core256M native CPU/fee/seed/output contract failed'
        }
    }
    if ($Chain -eq 'exact_components') {
        $exact = Read-PassingFixture 'exact-components-report.json'
        if (@($exact.runs).Count -ne 2 -or @($exact.runs | Where-Object {
            $_.passed -ne $true -or $_.outputFullKeyCount -ne 3000 -or $_.nativeCpuCalls -ne 1 -or
            $_.ripperCalls -ne 1 -or $_.executionFeeAE -ne 50 -or $_.nativeCpuFinished -ne $true -or $_.fullInventoryExact -ne $true
        }).Count -ne 0 -or $exact.runs[0].mode -ne 'manual' -or $exact.runs[1].mode -ne 'loop_card') {
            throw 'Both 3000-copy exact component orders must pass'
        }
    }
    if ($Chain -eq 'native_catalog') {
        $native = Read-PassingFixture 'native-catalog-report.json'
        $continuation = Read-PassingFixture 'native-continuation-roundtrip.json'
        if ($native.validNewScaledMapCount -ne 3 -or $native.nativeCpuIdle -ne $true -or $native.nativeMultiTickProgressObserved -ne $true -or
            $native.remainingSourceMaps -ne 0 -or $native.remainingPaper -ne 0 -or
            $continuation.roundTripPerformed -ne $true -or $continuation.cpuRoundTripPerformed -ne $true -or
            $continuation.completeCpuRoundTrip.passed -ne $true -or
            $continuation.totalExecutionFeeAE -ne 50 -or -not (Test-Path -LiteralPath (Join-Path $evidence 'native-cpu-first-commit.nbt'))) {
            throw 'Native map identities, CPU persistence or one-time fee contract failed'
        }
    }
    foreach ($frame in @('ae2lo-ripper-after.png','ae2lo-ripper-ui.png')) {
        $framePath = Join-Path $evidence ('screenshots/' + $frame)
        if (-not (Test-Path -LiteralPath $framePath -PathType Leaf) -or (Get-Item -LiteralPath $framePath).Length -le 8) { throw "Missing native framebuffer: $frame" }
    }
    $hidden = Get-Content -Raw -LiteralPath (Join-Path $evidence 'hidden-window-state.json') | ConvertFrom-Json
    if ($Generation -eq '1.21.1' -and $hidden.shutdownBudgetHookApplied -ne $true) {
        throw 'The production native shutdown Mixin did not transform the actual MinecraftServer class'
    }
    if ($hidden.clientTicksChecked -le 0 -or $hidden.visibleObservations -ne 0 -or $hidden.lastWindowVisible -ne 0 -or $hidden.normalStopObserved -ne $true) {
        throw 'Hidden-window observation or final normal-stop evidence failed'
    }
    $shutdown = Get-Content -Raw -LiteralPath (Join-Path $evidence 'client-shutdown.log')
    $client = Get-Content -Raw -LiteralPath (Join-Path $evidence 'client.log')
    if ($shutdown -notmatch 'save_complete saved=true' -or $shutdown -notmatch 'disconnect_complete serverShutdown=true integratedServerPresent=false' -or
        $shutdown -notmatch 'normal_stop_requested' -or $shutdown -match 'prepare_failed|disconnect_incomplete|prepare_refused' -or
        $client -notmatch 'All chunks are saved' -or $client -notmatch 'Stopping' -or
        $client -notmatch 'AE2LF background agent: GLFW creation forced invisible; show/focus disabled') {
        throw 'Missing complete native save, disconnect, hidden-agent or process shutdown evidence'
    }
    $summary['hiddenWindow'] = $hidden
    $summary['gameplayPassed'] = $true
    $summary['normalShutdown'] = $true
    $summary['status'] = 'passed'
} catch {
    $originalFailure = $_
    $summary['status'] = 'failed'
    $summary['failure'] = $_.Exception.Message
} finally {
    $env:JAVA_TOOL_OPTIONS = $oldOptions
    try {
        if ($stagedByThisRun -and (Test-Path -LiteralPath $staged)) {
            Assert-WorkspacePath $staged
            Remove-Item -LiteralPath $staged
        }
        foreach ($savedHelper in $removedHelpers) {
            if (Test-Path -LiteralPath $savedHelper.path) { throw "Refusing to overwrite a concurrently restored helper: $($savedHelper.path)" }
            Copy-Item -LiteralPath $savedHelper.backup -Destination $savedHelper.path
            if ((Get-Sha256 $savedHelper.path) -ne $savedHelper.sha256) { throw 'Restored helper hash differs' }
        }
        if ($null -ne $lock) {
            $afterMods = @(Get-ModSnapshot)
            Write-EvidenceJson 'run-mods-after.json' $afterMods
            if (($beforeMods | ConvertTo-Json -Depth 4 -Compress) -ne ($afterMods | ConvertTo-Json -Depth 4 -Compress)) { throw 'run/mods did not return to its exact original file/hash inventory' }
            $summary['runModsRestored'] = $true
        }
        if (-not $modsExisted -and (Test-Path -LiteralPath $runMods)) {
            Assert-WorkspacePath $runMods
            [IO.Directory]::Delete($runMods, $false)
        }
    } catch {
        $summary['status'] = 'failed'
        $summary['restoreFailure'] = $_.Exception.Message
        if ($null -eq $originalFailure) { $originalFailure = $_ }
    } finally {
        if ($null -ne $lock) { $lock.Dispose() }
        $summary['finishedAt'] = [DateTime]::UtcNow.ToString('o')
        Write-EvidenceJson 'background-summary.json' $summary
        Write-Output ($summary | ConvertTo-Json -Depth 8)
    }
}
if ($null -ne $originalFailure) { throw $originalFailure }
