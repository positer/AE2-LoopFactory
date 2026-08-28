param(
    [string]$PclRoot = $env:PCL_ROOT,
    [switch]$RefreshManagedFiles,
    [switch]$Recreate
)

$ErrorActionPreference = 'Stop'
$PclRoot = if ($PclRoot) { $PclRoot } else {
    throw 'Pass -PclRoot or set the PCL_ROOT environment variable.'
}
$refreshExisting = $RefreshManagedFiles -or $Recreate
$workspace = Split-Path -Parent $PSScriptRoot
$versionsRoot = Join-Path $PclRoot '.minecraft\versions'
$dependencyDownloads = @{
    'jei-1.21.1-neoforge-19.37.0.363.jar' =
        'https://maven.blamejared.com/mezz/jei/jei-1.21.1-neoforge/19.37.0.363/jei-1.21.1-neoforge-19.37.0.363.jar'
    'jei-26.1.2-neoforge-29.21.0.68.jar' =
        'https://maven.blamejared.com/mezz/jei/jei-26.1.2-neoforge/29.21.0.68/jei-26.1.2-neoforge-29.21.0.68.jar'
}

$instances = @(
    @{
        Name = 'AE2-lightoptimizer-1.21.1'
        SourceCandidates = @('1.21.1-NeoForge_21.1.235', 'AE2-lightoptimizer-1.21.1')
        Dependencies = @(
            'appliedenergistics2-19.2.17.jar'
            'guideme-21.1.17.jar'
            'jei-1.21.1-neoforge-19.37.0.363.jar'
        )
        Artifact = 'versions\neoforge-1.21.1\build\libs\ae2lightoptimizer-neoforge-mc1.21.1-0.1.0-SNAPSHOT.jar'
    }
    @{
        Name = 'AE2-lightoptimizer-26.1.2'
        SourceCandidates = @('neoforge-26.1.2.94', 'AE2-lightoptimizer-26.1.2')
        Dependencies = @(
            'appliedenergistics2-26.1.10-beta.jar'
            'guideme-26.1.12-beta.jar'
            'jei-26.1.2-neoforge-29.21.0.68.jar'
        )
        Artifact = 'versions\neoforge-26.1.2\build\libs\ae2lightoptimizer-neoforge-mc26.1.2-0.1.0-SNAPSHOT.jar'
    }
)

function Get-SaveManifestJson {
    param([Parameter(Mandatory)][string]$InstanceRoot)

    $savesRoot = Join-Path $InstanceRoot 'saves'
    if (-not (Test-Path -LiteralPath $savesRoot)) {
        return '[]'
    }

    $records = @(Get-ChildItem -LiteralPath $savesRoot -File -Recurse -Force |
        ForEach-Object {
            try {
                $sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
            } catch {
                throw "Cannot verify save integrity while a world file is locked. Close Minecraft before deployment: $($_.FullName)"
            }
            [pscustomobject]@{
                Path = [System.IO.Path]::GetRelativePath($savesRoot, $_.FullName)
                Length = $_.Length
                LastWriteTimeUtcTicks = $_.LastWriteTimeUtc.Ticks
                Sha256 = $sha256
            }
        } |
        Sort-Object Path)
    return (ConvertTo-Json -InputObject $records -Compress)
}

function Assert-SafeInstancePath {
    param(
        [Parameter(Mandatory)][string]$Target,
        [Parameter(Mandatory)][string]$ExpectedName
    )

    $resolvedTarget = [System.IO.Path]::GetFullPath($Target).TrimEnd('\')
    $resolvedVersionsRoot = [System.IO.Path]::GetFullPath($versionsRoot).TrimEnd('\')
    if (((Split-Path -Parent $resolvedTarget) -ne $resolvedVersionsRoot) -or
            ((Split-Path -Leaf $resolvedTarget) -ne $ExpectedName)) {
        throw "Refusing to modify unexpected instance path: $resolvedTarget"
    }
}

if ($Recreate) {
    Write-Warning '-Recreate is retained only as a compatibility alias for -RefreshManagedFiles. It never deletes an instance directory or runtime data.'
}

foreach ($instance in $instances) {
    $target = Join-Path $versionsRoot $instance.Name
    Assert-SafeInstancePath -Target $target -ExpectedName $instance.Name
    $saveManifestBefore = Get-SaveManifestJson -InstanceRoot $target
    $sourceName = $instance.SourceCandidates |
        Where-Object { Test-Path -LiteralPath (Join-Path $versionsRoot $_) } |
        Select-Object -First 1
    if (-not $sourceName) {
        throw "Missing local PCL source metadata for $($instance.Name)"
    }
    $source = Join-Path $versionsRoot $sourceName
    $staging = $null

    if ([System.IO.Path]::GetFullPath($source) -eq [System.IO.Path]::GetFullPath($target)) {
        $staging = Join-Path ([System.IO.Path]::GetTempPath()) `
            ("ae2lightoptimizer-pcl-stage-" + [guid]::NewGuid().ToString('N'))
        New-Item -ItemType Directory -Path $staging | Out-Null
        New-Item -ItemType Directory -Path (Join-Path $staging 'mods') | Out-Null
        New-Item -ItemType Directory -Path (Join-Path $staging 'PCL') | Out-Null
        Copy-Item -LiteralPath (Join-Path $source "$sourceName.json") `
            -Destination (Join-Path $staging "$sourceName.json")
        $sameSourceJar = Join-Path $source "$sourceName.jar"
        if (Test-Path -LiteralPath $sameSourceJar) {
            Copy-Item -LiteralPath $sameSourceJar -Destination $staging
        }
        Copy-Item -LiteralPath (Join-Path $source 'PCL\Setup.ini') `
            -Destination (Join-Path $staging 'PCL\Setup.ini')
        $sameSourcePclConfig = Join-Path $source 'PCL\config.json'
        if (Test-Path -LiteralPath $sameSourcePclConfig) {
            Copy-Item -LiteralPath $sameSourcePclConfig -Destination (Join-Path $staging 'PCL\config.json')
        }
        foreach ($dependency in $instance.Dependencies) {
            $sameSourceDependency = Join-Path $source "mods\$dependency"
            if (Test-Path -LiteralPath $sameSourceDependency) {
                Copy-Item -LiteralPath $sameSourceDependency -Destination (Join-Path $staging 'mods')
            }
        }
        $source = $staging
    }
    if (Test-Path -LiteralPath $target) {
        if (-not $refreshExisting) {
            throw "Instance already exists. Use -RefreshManagedFiles to update only managed launch and mod files: $target"
        }

        $expectedModNames = @($instance.Dependencies + (Split-Path -Leaf $instance.Artifact) | Sort-Object)
        $existingModFiles = @(Get-ChildItem -LiteralPath (Join-Path $target 'mods') -File -ErrorAction SilentlyContinue)
        $unknownModFiles = @($existingModFiles | Where-Object {
                $_.Name -notin $expectedModNames -and
                $_.Name -notmatch '^(?i)(ae2lightoptimizer-neoforge-|appliedenergistics2-|guideme-|jei-).+\.jar$'
            })
        if ($unknownModFiles) {
            $names = ($unknownModFiles.Name | Sort-Object) -join ', '
            throw "Refusing to delete or overwrite user-added mod files in $($instance.Name): $names"
        }
    }

    New-Item -ItemType Directory -Path $target -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $target 'mods') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $target 'PCL') -Force | Out-Null

    Get-ChildItem -LiteralPath (Join-Path $target 'mods') -File |
        Where-Object { $_.Name -match '^(?i)(ae2lightoptimizer-neoforge-|appliedenergistics2-|guideme-|jei-).+\.jar$' } |
        Remove-Item -Force

    $sourceJson = Join-Path $source ($sourceName + '.json')
    Copy-Item -LiteralPath $sourceJson -Destination (Join-Path $target ($instance.Name + '.json')) -Force
    $sourceJar = Join-Path $source ($sourceName + '.jar')
    if (Test-Path -LiteralPath $sourceJar) {
        Copy-Item -LiteralPath $sourceJar -Destination (Join-Path $target ($instance.Name + '.jar')) -Force
    }
    Copy-Item -LiteralPath (Join-Path $source 'PCL\Setup.ini') -Destination (Join-Path $target 'PCL\Setup.ini') -Force
    $sourcePclConfig = Join-Path $source 'PCL\config.json'
    if (Test-Path -LiteralPath $sourcePclConfig) {
        Copy-Item -LiteralPath $sourcePclConfig -Destination (Join-Path $target 'PCL\config.json') -Force
    }

    foreach ($dependency in $instance.Dependencies) {
        $dependencyPath = Join-Path $source "mods\$dependency"
        $dependencyTarget = Join-Path (Join-Path $target 'mods') $dependency
        if (Test-Path -LiteralPath $dependencyPath) {
            Copy-Item -LiteralPath $dependencyPath -Destination $dependencyTarget -Force
        } elseif ($dependencyDownloads.ContainsKey($dependency)) {
            Invoke-WebRequest -Uri $dependencyDownloads[$dependency] `
                -OutFile $dependencyTarget -UseBasicParsing
        } else {
            throw "Missing pinned dependency for $($instance.Name): $dependencyPath"
        }
    }
    Copy-Item -LiteralPath (Join-Path $workspace $instance.Artifact) -Destination (Join-Path $target 'mods') -Force

    $setup = Get-Content -Raw -LiteralPath (Join-Path $target 'PCL\Setup.ini')
    if ($setup -notmatch '(?m)^VersionArgumentIndieV2:True\r?$') {
        throw "PCL independent-instance mode is not enabled for $($instance.Name)"
    }
    $unexpectedMods = Get-ChildItem -LiteralPath (Join-Path $target 'mods') -File |
        Where-Object { $_.Name -match '(?i)immortalstorage|仙藏' }
    if ($unexpectedMods) {
        throw "ImmortalStorage artifact leaked into $($instance.Name)"
    }
    $deployedModNames = @(Get-ChildItem -LiteralPath (Join-Path $target 'mods') -File |
        Select-Object -ExpandProperty Name | Sort-Object)
    $expectedModNames = @($instance.Dependencies + (Split-Path -Leaf $instance.Artifact) | Sort-Object)
    if (Compare-Object $expectedModNames $deployedModNames) {
        throw "Dependency whitelist mismatch in $($instance.Name)"
    }
    $links = Get-ChildItem -LiteralPath $target -Force |
        Where-Object { $_.LinkType }
    if ($links) {
        throw "Filesystem links are forbidden in isolated PCL instance $($instance.Name)"
    }

    $saveManifestAfter = Get-SaveManifestJson -InstanceRoot $target
    if ($saveManifestAfter -cne $saveManifestBefore) {
        throw "Save-integrity guard failed for $($instance.Name): saves changed during managed-file deployment"
    }

    Write-Output "Refreshed managed PCL files without changing saves: $target"

    if ($staging) {
        $resolvedStaging = (Resolve-Path -LiteralPath $staging).Path
        $resolvedTemp = (Resolve-Path -LiteralPath ([System.IO.Path]::GetTempPath())).Path.TrimEnd('\')
        if ((Split-Path -Parent $resolvedStaging) -ne $resolvedTemp -or
                (Split-Path -Leaf $resolvedStaging) -notlike 'ae2lightoptimizer-pcl-stage-*') {
            throw "Refusing to remove unexpected staging path: $resolvedStaging"
        }
        Remove-Item -LiteralPath $resolvedStaging -Recurse -Force
    }
}
