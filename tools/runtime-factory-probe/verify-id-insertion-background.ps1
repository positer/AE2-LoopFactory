param(
    [Parameter(Mandatory=$true)][ValidateSet('1.21.1','26.1.2')][string]$Generation,
    [Parameter(Mandatory=$true)][ValidateSet('none','jei','emi')][string]$Mode,
    [Parameter(Mandatory=$true)][ValidatePattern('^[A-Za-z0-9_.-]+$')][string]$EvidenceName
)
# Isolate real viewer input tests; the delegated runner owns gameplay, native input and normal shutdown.
$ErrorActionPreference = 'Stop'
$Mode = $Mode.ToLowerInvariant()
if ($Generation -eq '26.1.2' -and $Mode -eq 'emi') {
    throw 'No pinned official EMI build exists for 26.1.2; modern EMI cannot be tested by this wrapper'
}
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$projectRoot = [IO.Path]::GetFullPath((Join-Path $taskRoot "versions/neoforge-$Generation"))
$runRoot = Join-Path $projectRoot 'run'
$runMods = [IO.Path]::GetFullPath((Join-Path $runRoot 'mods'))
$runner = Join-Path $PSScriptRoot 'verify-background.ps1'
$helperSource = Join-Path $PSScriptRoot "$Generation/build/libs/ae2lf-runtime-probe-mc$Generation-1.jar"
$dependencyRoot = "C:/Users/12252/Desktop/Files/Minecraft/PCL/.minecraft/versions/AE2-lightoptimizer-$Generation/mods"
$runnerEvidence = Join-Path $taskRoot "archive/2026-09-08-background-full-audit/$EvidenceName"
$wrapperEvidence = [IO.Path]::GetFullPath((Join-Path $taskRoot "archive/2026-09-12-expanded-background-qa/$EvidenceName-id-isolation"))
$backupFiles = Join-Path $wrapperEvidence 'run-mods-original'
$viewerCache = Join-Path $taskRoot 'archive/2026-09-12-expanded-background-qa/new-id-insertion/viewer-inputs'

# Full NeoForge JARs fetched from official publishers and inspected before this script was authored.
$viewerPins = @{
    '1.21.1/jei' = [ordered]@{
        name='jei-1.21.1-neoforge-19.27.0.343.jar';version='19.27.0.343'
        url='https://maven.blamejared.com/mezz/jei/jei-1.21.1-neoforge/19.27.0.343/jei-1.21.1-neoforge-19.27.0.343.jar'
        sha256='137f60018968a50c6f00e3a8ca9be790391792d2f3e6eff66237f2e9b3bc1353'
    }
    '26.1.2/jei' = [ordered]@{
        name='jei-26.1.2-neoforge-29.21.0.68.jar';version='29.21.0.68'
        url='https://maven.blamejared.com/mezz/jei/jei-26.1.2-neoforge/29.21.0.68/jei-26.1.2-neoforge-29.21.0.68.jar'
        sha256='8e32639214ecb51a3c287844920eb62cd52d6ea6e3356605fa1b584ff5321337'
    }
    '1.21.1/emi' = [ordered]@{
        name='emi-neoforge-1.1.24+1.21.1.jar';version='1.1.24+1.21.1'
        url='https://repo.sleeping.town/dev/emi/emi-neoforge/1.1.24+1.21.1/emi-neoforge-1.1.24+1.21.1.jar'
        sha256='b68691f94f0727fc517cac2ab55bd6658d1179cc8cc715fe610b60c37469bc4c'
    }
}
function Assert-ChildPath([string]$Path, [string]$Parent) {
    $resolvedPath = [IO.Path]::GetFullPath($Path)
    $resolvedParent = [IO.Path]::GetFullPath($Parent).TrimEnd('\','/') + [IO.Path]::DirectorySeparatorChar
    if (-not $resolvedPath.StartsWith($resolvedParent, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing path outside the declared directory: $resolvedPath"
    }
}
function Get-FileSha256([string]$Path) { (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() }
function Write-IsolationJson([string]$Name, $Value) {
    [IO.File]::WriteAllText((Join-Path $wrapperEvidence $Name), (ConvertTo-Json -InputObject $Value -Depth 20), (New-Object Text.UTF8Encoding($false)))
}
function Assert-NoReparsePoints([string]$Directory) {
    $entries = @(Get-Item -LiteralPath $Directory) + @(Get-ChildItem -LiteralPath $Directory -Recurse -Force)
    if (@($entries | Where-Object { ($_.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0 }).Count -gt 0) {
        throw "Refusing a directory tree containing reparse points: $Directory"
    }
}
function Get-ModManifest {
    foreach ($file in @(Get-ChildItem -LiteralPath $runMods -File -Recurse -Force | Sort-Object FullName)) {
        Assert-ChildPath $file.FullName $runMods
        [ordered]@{name=$file.FullName.Substring($runMods.Length + 1);bytes=$file.Length;sha256=(Get-FileSha256 $file.FullName)}
    }
}
function Assert-NoActiveGenerationClient {
    $projectToken = $projectRoot.Replace('\','/').ToLowerInvariant()
    $active = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'" | Where-Object {
        $_.CommandLine -and $_.CommandLine.Replace('\','/').ToLowerInvariant().Contains($projectToken)
    })
    if ($active.Count -gt 0) {
        throw "Generation $Generation still has Java processes using its project: $($active.ProcessId -join ', '); run/mods must wait for normal exit"
    }
}
function Get-PinnedViewer {
    if ($Mode -eq 'none') { return $null }
    $pin = $viewerPins["$Generation/$Mode"]
    $source = Join-Path $viewerCache $pin.name
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
        $downloads = Join-Path $wrapperEvidence 'downloaded-inputs'
        New-Item -ItemType Directory -Path $downloads | Out-Null
        $source = Join-Path $downloads $pin.name
        Assert-ChildPath $source $downloads
        # A new evidence directory makes this download write-once; partial/invalid input is retained on failure.
        Invoke-WebRequest -UseBasicParsing -Uri $pin.url -OutFile $source
    }
    if ((Get-FileSha256 $source) -ne $pin.sha256) { throw "Pinned official viewer hash mismatch: $source" }
    [ordered]@{name=$pin.name;path=$source;version=$pin.version;url=$pin.url;sha256=$pin.sha256;role='viewer'}
}
function Assert-StagedProfile {
    $active = @(Get-ModManifest | Where-Object { $_.name -match '(?i)\.jar$' })
    if ($active.Count -ne $stagedJars.Count) { throw 'Active staged JAR count does not match the isolated viewer profile' }
    foreach ($entry in $active) {
        if (-not $stagedJars.ContainsKey($entry.name) -or $stagedJars[$entry.name] -ne $entry.sha256) {
            throw "Unapproved active JAR in viewer profile: $($entry.name)"
        }
    }
}

Assert-ChildPath $runMods $projectRoot
Assert-ChildPath $wrapperEvidence $taskRoot
foreach ($required in @($runner,$helperSource)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Build/integrate the pinned runner and helper before testing: $required" }
}
$runnerParameters = (Get-Command -Name $runner -CommandType ExternalScript -ErrorAction Stop).Parameters
if (-not $runnerParameters.ContainsKey('IdInsertionOnly') -or -not $runnerParameters.ContainsKey('IdInsertionViewer')) {
    throw 'The base runner must implement -IdInsertionOnly and -IdInsertionViewer; environment-only overrides are intentionally unsupported'
}
if (-not (Test-Path -LiteralPath $runMods -PathType Container)) { throw 'Expected an existing development run/mods directory' }
if (Test-Path -LiteralPath $runnerEvidence) { throw "Refusing to overwrite gameplay evidence: $runnerEvidence" }
if (Test-Path -LiteralPath $wrapperEvidence) { throw "Refusing to overwrite isolation evidence: $wrapperEvidence" }
$baselineFiles = @(Get-ChildItem -LiteralPath $dependencyRoot -File | Where-Object { $_.Name -like 'AppliedFlux*' -or $_.Name -like 'Glodium*' })
foreach ($prefix in @('AppliedFlux','Glodium')) {
    if (@($baselineFiles | Where-Object { $_.Name -like "$prefix*.jar" }).Count -ne 1) {
        throw "Expected exactly one pinned active $prefix baseline JAR for $Generation"
    }
}
Assert-NoActiveGenerationClient
$mutex = [Threading.Mutex]::new($false, "Local\AE2LF-Development-$Generation")
$mutexOwned = $false
$fileLock = $null
$before = @()
$beforeByName = @{}
$removedByName = @{}
$knownWrites = @{}
$stagedJars = @{}
$pinnedInputs = @()
$backupComplete = $false
$modsTouched = $false
$originalFailure = $null
$summary = [ordered]@{generation=$Generation;viewerMode=$Mode;runnerEvidence=$runnerEvidence;status='preparing';startedAt=[DateTime]::UtcNow.ToString('o')}
try {
    try { $mutexOwned = $mutex.WaitOne(0) }
    catch [Threading.AbandonedMutexException] { $mutexOwned = $true }
    if (-not $mutexOwned) { throw "Another isolated audit owns generation $Generation" }
    $fileLock = [IO.File]::Open((Join-Path $runRoot '.ae2lo-ripper-background.lock'), [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
    Assert-NoActiveGenerationClient
    Assert-NoReparsePoints $runMods
    New-Item -ItemType Directory -Path $wrapperEvidence | Out-Null
    New-Item -ItemType Directory -Path $backupFiles | Out-Null
    $viewer = Get-PinnedViewer
    $pinnedInputs += [ordered]@{name=[IO.Path]::GetFileName($helperSource);path=$helperSource;sha256=(Get-FileSha256 $helperSource);role='helper'}
    foreach ($file in $baselineFiles) {
        # The base runner also copies these prefixes' inactive .bak files; own those writes explicitly.
        $pinnedInputs += [ordered]@{name=$file.Name;path=$file.FullName;sha256=(Get-FileSha256 $file.FullName);role='read-only PCL baseline'}
    }
    if ($null -ne $viewer) { $pinnedInputs += $viewer }
    foreach ($inputFile in $pinnedInputs) {
        if ($knownWrites.ContainsKey($inputFile.name)) { throw "Duplicate staged input name: $($inputFile.name)" }
        $knownWrites[$inputFile.name] = $inputFile.sha256
        if ($inputFile.name -match '(?i)\.jar$') { $stagedJars[$inputFile.name] = $inputFile.sha256 }
    }
    Write-IsolationJson 'pinned-inputs.json' $pinnedInputs
    Write-IsolationJson 'known-writes.json' $knownWrites
    $before = @(Get-ModManifest)
    Write-IsolationJson 'run-mods-before.json' $before
    foreach ($entry in $before) {
        $source = Join-Path $runMods $entry.name
        $saved = Join-Path $backupFiles $entry.name
        Assert-ChildPath $saved $backupFiles
        New-Item -ItemType Directory -Force -Path ([IO.Path]::GetDirectoryName($saved)) | Out-Null
        Copy-Item -LiteralPath $source -Destination $saved
        if ((Get-FileSha256 $saved) -ne $entry.sha256) { throw "Original-file backup differs: $($entry.name)" }
        $beforeByName[$entry.name] = $entry
    }
    $backupComplete = $true
    $removed = @($before | Where-Object { $_.name -match '(?i)\.jar$' })
    Write-IsolationJson 'temporarily-removed.json' $removed
    Assert-NoActiveGenerationClient
    foreach ($entry in $removed) {
        $path = Join-Path $runMods $entry.name
        Assert-ChildPath $path $runMods
        if ((Get-FileSha256 $path) -ne $entry.sha256) { throw "Mod changed after backup: $($entry.name)" }
        $removedByName[$entry.name] = $true
        $modsTouched = $true
        Remove-Item -LiteralPath $path
    }
    foreach ($inputFile in $pinnedInputs) {
        if ((Get-FileSha256 $inputFile.path) -ne $inputFile.sha256) { throw "Pinned input changed before staging: $($inputFile.path)" }
        $destination = Join-Path $runMods $inputFile.name
        Assert-ChildPath $destination $runMods
        if (Test-Path -LiteralPath $destination) {
            if (-not $beforeByName.ContainsKey($inputFile.name) -or (Get-FileSha256 $destination) -ne $beforeByName[$inputFile.name].sha256) {
                throw "Unowned file appeared at a staging destination: $destination"
            }
        }
        $modsTouched = $true
        Copy-Item -LiteralPath $inputFile.path -Destination $destination -Force
        if ((Get-FileSha256 $destination) -ne $inputFile.sha256) { throw "Staged input differs: $($inputFile.name)" }
    }
    Assert-StagedProfile
    Write-IsolationJson 'run-mods-before-runner.json' @(Get-ModManifest)
    $summary['status'] = 'running'
    Write-IsolationJson 'id-insertion-wrapper-summary.json' $summary
    Write-Output "ID insertion profile ready: $Generation/$Mode; $($stagedJars.Count) active JARs; original bytes backed up at $backupFiles"
    & $runner -Generation $Generation -EvidenceName $EvidenceName -IdInsertionOnly -IdInsertionViewer $Mode
    $report = Get-Content -Raw -LiteralPath (Join-Path $runnerEvidence 'item-id-insertion-report.json') | ConvertFrom-Json
    $expectJei = $Mode -eq 'jei'
    $expectEmi = $Mode -eq 'emi'
    if ($report.status -ne 'passed' -or $report.viewerMode -ne $Mode -or
        $report.loadedJei -ne $expectJei -or $report.loadedEmi -ne $expectEmi) {
        throw 'Real runtime viewer loading/report mode did not match the requested isolated profile'
    }
    $ui = Get-Content -Raw -LiteralPath (Join-Path $runnerEvidence 'ui-item-block-audit.json') | ConvertFrom-Json
    if ($ui.probeProperties.'ae2lf.probe.viewerMode' -ne $Mode) { throw 'The base runner did not pass the requested viewerMode into the real client' }
    if (-not (Test-Path -LiteralPath (Join-Path $runnerEvidence 'normal-shutdown.txt')) -or
        (Test-Path -LiteralPath (Join-Path $runnerEvidence 'shutdown-timeout.txt'))) { throw 'Input audit did not exit through normal native shutdown' }
    Assert-NoActiveGenerationClient
    Assert-StagedProfile
    $summary['runnerPassed'] = $true
    $summary['status'] = 'passed'
} catch {
    $originalFailure = $_
    $summary['status'] = 'failed'
    $summary['failure'] = $_.Exception.Message
} finally {
    if ($backupComplete -and $modsTouched) {
        try {
            Assert-NoActiveGenerationClient
            Assert-NoReparsePoints $runMods
            Assert-NoReparsePoints $backupFiles
            $afterRun = @(Get-ModManifest)
            Write-IsolationJson 'run-mods-after-runner.json' $afterRun
            $unownedChanges = New-Object System.Collections.Generic.List[string]
            foreach ($entry in $afterRun) {
                $path = Join-Path $runMods $entry.name
                Assert-ChildPath $path $runMods
                if ($beforeByName.ContainsKey($entry.name)) {
                    if ($entry.sha256 -eq $beforeByName[$entry.name].sha256) { continue }
                    if ($knownWrites.ContainsKey($entry.name) -and $knownWrites[$entry.name] -eq $entry.sha256) { continue }
                    $unownedChanges.Add("changed: $($entry.name)")
                    # Preserve unknown modifications at their current path and in evidence; do not overwrite them.
                    $observed = Join-Path (Join-Path $wrapperEvidence 'unowned-changes') $entry.name
                    Assert-ChildPath $observed $wrapperEvidence
                    New-Item -ItemType Directory -Force -Path ([IO.Path]::GetDirectoryName($observed)) | Out-Null
                    Copy-Item -LiteralPath $path -Destination $observed
                } elseif ($knownWrites.ContainsKey($entry.name) -and $knownWrites[$entry.name] -eq $entry.sha256) {
                    Remove-Item -LiteralPath $path
                } else { $unownedChanges.Add("new: $($entry.name)") }
            }
            foreach ($entry in $before) {
                $path = Join-Path $runMods $entry.name
                $saved = Join-Path $backupFiles $entry.name
                Assert-ChildPath $path $runMods
                Assert-ChildPath $saved $backupFiles
                if ((Get-FileSha256 $saved) -ne $entry.sha256) { throw "Immutable original backup changed: $($entry.name)" }
                if (Test-Path -LiteralPath $path -PathType Container) { $unownedChanges.Add("replaced by directory: $($entry.name)"); continue }
                $present = Test-Path -LiteralPath $path -PathType Leaf
                if ($present) {
                    $observedHash = Get-FileSha256 $path
                    if ($observedHash -eq $entry.sha256) { continue }
                    if (-not $knownWrites.ContainsKey($entry.name) -or $knownWrites[$entry.name] -ne $observedHash) { continue }
                } elseif (-not $removedByName.ContainsKey($entry.name)) {
                    $unownedChanges.Add("missing unexpectedly: $($entry.name)")
                }
                New-Item -ItemType Directory -Force -Path ([IO.Path]::GetDirectoryName($path)) | Out-Null
                Copy-Item -LiteralPath $saved -Destination $path -Force
                if ((Get-FileSha256 $path) -ne $entry.sha256) { throw "Restored original hash differs: $($entry.name)" }
            }
            $restored = @(Get-ModManifest)
            Write-IsolationJson 'run-mods-restored.json' $restored
            Write-IsolationJson 'unowned-changes.json' @($unownedChanges)
            foreach ($inputFile in $pinnedInputs) {
                if ((Get-FileSha256 $inputFile.path) -ne $inputFile.sha256) { throw "Read-only pinned input changed: $($inputFile.path)" }
            }
            if ($unownedChanges.Count -gt 0) { throw "Preserved unowned changes; exact restoration failed: $($unownedChanges -join ', ')" }
            if ((ConvertTo-Json -InputObject $before -Depth 4 -Compress) -ne (ConvertTo-Json -InputObject $restored -Depth 4 -Compress)) {
                throw 'Original run/mods inventory and SHA256 values were not restored exactly'
            }
            $summary['runModsRestored'] = $true
        } catch {
            $summary['status'] = 'failed'
            $summary['restoreFailure'] = $_.Exception.Message
            if ($null -eq $originalFailure) { $originalFailure = $_ }
        }
    }
    if ($null -ne $fileLock) { $fileLock.Dispose() }
    if ($mutexOwned) { $mutex.ReleaseMutex() }
    $mutex.Dispose()
    $summary['finishedAt'] = [DateTime]::UtcNow.ToString('o')
    if (Test-Path -LiteralPath $wrapperEvidence -PathType Container) { Write-IsolationJson 'id-insertion-wrapper-summary.json' $summary }
    Write-Output (ConvertTo-Json -InputObject $summary -Depth 10)
}
if ($null -ne $originalFailure) { throw $originalFailure }
