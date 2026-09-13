param(
    [Parameter(Mandatory=$true)][ValidateSet('1.21.1')][string]$Generation,
    [Parameter(Mandatory=$true)][ValidatePattern('^[A-Za-z0-9_.-]+$')][string]$EvidenceName,
    [ValidateSet('MekOnly')][string]$Mode='MekOnly',
    [ValidateRange(96,1536)][int]$MekBatches=96,
    [ValidateRange(20,100)][int]$TickRate=20
)
# Isolated wrapper only. The delegated runner owns native gameplay and its existing strict gate.
# Example: -Generation 1.21.1 -EvidenceName expanded-mek-1.21.1-run1 -MekBatches 384 -TickRate 100
$ErrorActionPreference = 'Stop'
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$projectRoot = [IO.Path]::GetFullPath((Join-Path $taskRoot "versions/neoforge-$Generation"))
$runRoot = Join-Path $projectRoot 'run'
$runMods = [IO.Path]::GetFullPath((Join-Path $runRoot 'mods'))
$runner = Join-Path $PSScriptRoot 'verify-background.ps1'
$runnerEvidence = Join-Path $taskRoot "archive/2026-09-08-background-full-audit/$EvidenceName"
$wrapperEvidence = [IO.Path]::GetFullPath((Join-Path $taskRoot "archive/2026-09-12-expanded-background-qa/$EvidenceName-isolation"))
$backupFiles = Join-Path $wrapperEvidence 'run-mods-original'
$mekSource = Join-Path $taskRoot 'archive/2026-09-09-mekanism-bulk/inputs/Mekanism-1.21.1-10.7.19.85.jar'
$helperSource = Join-Path $PSScriptRoot "$Generation/build/libs/ae2lf-runtime-probe-mc$Generation-1.jar"

function Assert-ChildPath([string]$Path, [string]$Parent) {
    $resolvedPath = [IO.Path]::GetFullPath($Path)
    $resolvedParent = [IO.Path]::GetFullPath($Parent).TrimEnd('\','/') + [IO.Path]::DirectorySeparatorChar
    if (-not $resolvedPath.StartsWith($resolvedParent, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing path outside the declared directory: $resolvedPath"
    }
}
function Get-FileSha256([string]$Path) { (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() }
function Write-WrapperJson([string]$Name, $Value) {
    [IO.File]::WriteAllText((Join-Path $wrapperEvidence $Name), ($Value | ConvertTo-Json -Depth 15), (New-Object Text.UTF8Encoding($false)))
}
function Get-ModManifest {
    foreach ($file in @(Get-ChildItem -LiteralPath $runMods -File -Recurse -Force | Sort-Object FullName)) {
        Assert-ChildPath $file.FullName $runMods
        [ordered]@{name=$file.FullName.Substring($runMods.Length + 1);bytes=$file.Length;sha256=(Get-FileSha256 $file.FullName)}
    }
}
function Assert-NoActiveGenerationClient {
    # The base full runner predates cooperative locks. Refuse an already running native dev client too.
    $projectToken = $projectRoot.Replace('\','/').ToLowerInvariant()
    $active = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'" | Where-Object {
        $_.CommandLine -and $_.CommandLine.Replace('\','/').ToLowerInvariant().Contains($projectToken)
    })
    if ($active.Count -gt 0) {
        throw "Generation $Generation still has native Java processes using this project: $($active.ProcessId -join ', '). Wait for their normal exit before changing run/mods."
    }
}

Assert-ChildPath $runMods $projectRoot
Assert-ChildPath $wrapperEvidence $taskRoot
foreach ($required in @($runner,$mekSource,$helperSource)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Missing pinned runner/input; no dependency was downloaded or replaced: $required" }
}
if (-not (Test-Path -LiteralPath $runMods -PathType Container)) { throw 'Expected an existing generation development run/mods directory' }
if (Test-Path -LiteralPath $runnerEvidence) { throw "Refusing to overwrite runner evidence: $runnerEvidence" }
if (Test-Path -LiteralPath $wrapperEvidence) { throw "Refusing to overwrite isolation evidence: $wrapperEvidence" }
Assert-NoActiveGenerationClient
$mutex = [Threading.Mutex]::new($false, "Local\AE2LF-Development-$Generation")
$mutexOwned = $false
$fileLock = $null
$before = @()
$beforeByName = @{}
$backupComplete = $false
$modsTouched = $false
$originalFailure = $null
$summary = [ordered]@{generation=$Generation;mode=$Mode;mekBatches=$MekBatches;tickRate=$TickRate;runnerEvidence=$runnerEvidence;status='preparing';startedAt=[DateTime]::UtcNow.ToString('o')}
try {
    try { $mutexOwned = $mutex.WaitOne(0) }
    catch [Threading.AbandonedMutexException] { $mutexOwned = $true }
    if (-not $mutexOwned) { throw "Another focused audit owns generation $Generation" }
    # Coordinate with the ripper wrapper without changing either existing runner.
    $fileLock = [IO.File]::Open((Join-Path $runRoot '.ae2lo-ripper-background.lock'), [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
    Assert-NoActiveGenerationClient
    $treeEntries = @(
        Get-Item -LiteralPath $runMods
        Get-ChildItem -LiteralPath $runMods -Recurse -Force
    )
    $reparsePoints = @($treeEntries | Where-Object {
        ($_.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0
    })
    if (@($reparsePoints).Count -gt 0) { throw 'Refusing to stage a run/mods tree containing reparse points' }
    New-Item -ItemType Directory -Path $backupFiles -Force | Out-Null
    $before = @(Get-ModManifest)
    Write-WrapperJson 'run-mods-before.json' $before
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
    # These remain the exact original pinned source files; only destination copies are temporary.
    $knownWrites = @{}
    $knownWrites[[IO.Path]::GetFileName($mekSource)] = Get-FileSha256 $mekSource
    $knownWrites[[IO.Path]::GetFileName($helperSource)] = Get-FileSha256 $helperSource
    Write-WrapperJson 'pinned-inputs.json' ([ordered]@{
        mekanism=[ordered]@{path=$mekSource;sha256=(Get-FileSha256 $mekSource);provenance='Existing pinned archive input; source preserved without modification'}
        helper=[ordered]@{path=$helperSource;sha256=(Get-FileSha256 $helperSource)}
        baseRunner=$runner
    })
    # The native MEK fixture requires only item/fluid AE keys. Keep inactive .bak files untouched.
    $removed = @($before | Where-Object {
        $_.name -notmatch '[/\\]' -and $_.name -match '^(AppliedFlux|Glodium).*\.jar$'
    })
    Assert-NoActiveGenerationClient
    foreach ($entry in $removed) {
        $path = Join-Path $runMods $entry.name
        Assert-ChildPath $path $runMods
        if ((Get-FileSha256 $path) -ne $entry.sha256) { throw "Mod changed after backup: $($entry.name)" }
        $modsTouched = $true
        Remove-Item -LiteralPath $path
    }
    Write-WrapperJson 'temporarily-removed.json' $removed
    Write-WrapperJson 'run-mods-before-runner.json' @(Get-ModManifest)
    $summary['status'] = 'running'
    Write-WrapperJson 'focused-wrapper-summary.json' $summary
    Write-Output "Focused $Mode isolation ready for $Generation; removed $($removed.Count) active optional storage-addon files; original bytes retained at $backupFiles"
    $modsTouched = $true
    & $runner -Generation $Generation -EvidenceName $EvidenceName -MekOnly -MekBatches $MekBatches -TickRate $TickRate
    $summary['runnerPassed'] = $true
    $summary['status'] = 'passed'
} catch {
    $originalFailure = $_
    $summary['status'] = 'failed'
    $summary['failure'] = $_.Exception.Message
} finally {
    if ($backupComplete -and $modsTouched) {
        try {
            # Never restore JARs while this generation still has a native client using them.
            Assert-NoActiveGenerationClient
            $afterRun = @(Get-ModManifest)
            Write-WrapperJson 'run-mods-after-runner.json' $afterRun
            $unexpectedNew = New-Object System.Collections.Generic.List[string]
            foreach ($entry in $afterRun) {
                if ($beforeByName.ContainsKey($entry.name)) { continue }
                $path = Join-Path $runMods $entry.name
                Assert-ChildPath $path $runMods
                if ($knownWrites.ContainsKey($entry.name) -and $knownWrites[$entry.name] -eq $entry.sha256) {
                    Remove-Item -LiteralPath $path
                } else {
                    # An unrelated new file is not owned by this wrapper; retain it and fail restoration.
                    $unexpectedNew.Add($entry.name)
                }
            }
            foreach ($entry in $before) {
                $path = Join-Path $runMods $entry.name
                $saved = Join-Path $backupFiles $entry.name
                Assert-ChildPath $path $runMods
                Assert-ChildPath $saved $backupFiles
                if ((Get-FileSha256 $saved) -ne $entry.sha256) { throw "Immutable backup changed: $($entry.name)" }
                if (-not (Test-Path -LiteralPath $path -PathType Leaf) -or (Get-FileSha256 $path) -ne $entry.sha256) {
                    if (Test-Path -LiteralPath $path -PathType Leaf) {
                        $observed = Join-Path (Join-Path $wrapperEvidence 'changed-files-before-restore') $entry.name
                        Assert-ChildPath $observed $wrapperEvidence
                        New-Item -ItemType Directory -Force -Path ([IO.Path]::GetDirectoryName($observed)) | Out-Null
                        Copy-Item -LiteralPath $path -Destination $observed
                    }
                    New-Item -ItemType Directory -Force -Path ([IO.Path]::GetDirectoryName($path)) | Out-Null
                    Copy-Item -LiteralPath $saved -Destination $path -Force
                }
                if ((Get-FileSha256 $path) -ne $entry.sha256) { throw "Restored original hash differs: $($entry.name)" }
            }
            $restored = @(Get-ModManifest)
            Write-WrapperJson 'run-mods-restored.json' $restored
            if ($unexpectedNew.Count -gt 0) { throw "Preserved unowned new files; original inventory cannot be declared restored: $($unexpectedNew -join ', ')" }
            if (($before | ConvertTo-Json -Depth 4 -Compress) -ne ($restored | ConvertTo-Json -Depth 4 -Compress)) {
                throw 'Original run/mods file inventory and SHA-256 values were not restored exactly'
            }
            if ((Get-FileSha256 $mekSource) -ne $knownWrites[[IO.Path]::GetFileName($mekSource)] -or
                (Get-FileSha256 $helperSource) -ne $knownWrites[[IO.Path]::GetFileName($helperSource)]) {
                throw 'A pinned source input changed during the delegated run'
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
    if (Test-Path -LiteralPath $wrapperEvidence -PathType Container) { Write-WrapperJson 'focused-wrapper-summary.json' $summary }
    Write-Output ($summary | ConvertTo-Json -Depth 8)
}
if ($null -ne $originalFailure) { throw $originalFailure }
