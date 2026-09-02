$ErrorActionPreference = 'Stop'
$workspace = Split-Path -Parent $PSScriptRoot
$testRoot = Join-Path ([System.IO.Path]::GetTempPath()) `
    ('ae2lightoptimizer-pcl-preservation-test-' + [guid]::NewGuid().ToString('N'))

function Get-TreeManifestJson {
    param([Parameter(Mandatory)][string]$Root)

    $records = @(Get-ChildItem -LiteralPath $Root -File -Recurse -Force |
        ForEach-Object {
            [pscustomobject]@{
                Path = [System.IO.Path]::GetRelativePath($Root, $_.FullName)
                Length = $_.Length
                LastWriteTimeUtcTicks = $_.LastWriteTimeUtc.Ticks
                Sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
            }
        } |
        Sort-Object Path)
    return (ConvertTo-Json -InputObject $records -Compress)
}

try {
    $versionsRoot = Join-Path $testRoot '.minecraft\versions'
    $fixtures = @(
        @{
            Source = '1.21.1-NeoForge_21.1.235'
            Target = 'AE2-lightoptimizer-1.21.1'
            Dependencies = @(
                'appliedenergistics2-19.2.17.jar'
                'guideme-21.1.17.jar'
                'jei-1.21.1-neoforge-19.37.0.363.jar'
                'AppliedFlux-1.21-2.1.5-neoforge.jar'
                'Glodium-1.21-2.2-neoforge.jar'
            )
        }
        @{
            Source = 'neoforge-26.1.2.94'
            Target = 'AE2-lightoptimizer-26.1.2'
            Dependencies = @(
                'appliedenergistics2-26.1.10-beta.jar'
                'guideme-26.1.12-beta.jar'
                'jei-26.1.2-neoforge-29.21.0.68.jar'
                'AppliedFlux-26.1-1.0.1-neoforge.jar'
                'Glodium-26.1-1.2-neoforge.jar'
            )
        }
    )

    $before = @{}
    foreach ($fixture in $fixtures) {
        $source = Join-Path $versionsRoot $fixture.Source
        $target = Join-Path $versionsRoot $fixture.Target
        New-Item -ItemType Directory -Path (Join-Path $source 'mods') -Force | Out-Null
        New-Item -ItemType Directory -Path (Join-Path $source 'PCL') -Force | Out-Null
        Set-Content -LiteralPath (Join-Path $source ($fixture.Source + '.json')) -Value '{}' -NoNewline
        Set-Content -LiteralPath (Join-Path $source 'PCL\Setup.ini') -Value 'VersionArgumentIndieV2:True'
        foreach ($dependency in $fixture.Dependencies) {
            Set-Content -LiteralPath (Join-Path $source "mods\$dependency") -Value $dependency -NoNewline
        }

        $preservedFiles = @(
            'saves\Regression World\level.dat'
            'config\runtime.toml'
            'defaultconfigs\server.toml'
            'screenshots\proof.txt'
            'resourcepacks\local-pack.txt'
            'logs\latest.log'
            'options.txt'
        )
        foreach ($relativePath in $preservedFiles) {
            $path = Join-Path $target $relativePath
            New-Item -ItemType Directory -Path (Split-Path -Parent $path) -Force | Out-Null
            Set-Content -LiteralPath $path -Value "preserve:$relativePath" -NoNewline
        }
        New-Item -ItemType Directory -Path (Join-Path $target 'mods') -Force | Out-Null
        New-Item -ItemType Directory -Path (Join-Path $target 'PCL') -Force | Out-Null
        Set-Content -LiteralPath (Join-Path $target 'mods\ae2lightoptimizer-neoforge-stale.jar') -Value 'stale' -NoNewline
        $before[$fixture.Target] = Get-TreeManifestJson -Root $target
    }

    & (Join-Path $workspace 'tools\provision_pcl_instances.ps1') `
        -PclRoot $testRoot -RefreshManagedFiles
    & (Join-Path $workspace 'tools\provision_pcl_instances.ps1') `
        -PclRoot $testRoot -Recreate

    foreach ($fixture in $fixtures) {
        $target = Join-Path $versionsRoot $fixture.Target
        $managedRelativePaths = @(
            'mods'
            'PCL'
            ($fixture.Target + '.json')
            ($fixture.Target + '.jar')
        )
        $preservedBefore = @($before[$fixture.Target] | ConvertFrom-Json | Where-Object {
                $top = ($_.Path -split '[\\/]')[0]
                $top -notin @('mods', 'PCL') -and $_.Path -notin $managedRelativePaths
            }) | ConvertTo-Json -Compress
        $preservedAfter = @(Get-TreeManifestJson -Root $target | ConvertFrom-Json | Where-Object {
                $top = ($_.Path -split '[\\/]')[0]
                $top -notin @('mods', 'PCL') -and $_.Path -notin $managedRelativePaths
            }) | ConvertTo-Json -Compress
        if ($preservedAfter -cne $preservedBefore) {
            throw "Runtime-state preservation regression in $($fixture.Target)"
        }
    }

    Write-Output 'PASS: -RefreshManagedFiles and legacy -Recreate preserved saves and all representative runtime-state files in both generations.'
} finally {
    if (Test-Path -LiteralPath $testRoot) {
        $resolvedTestRoot = (Resolve-Path -LiteralPath $testRoot).Path
        $resolvedTemp = (Resolve-Path -LiteralPath ([System.IO.Path]::GetTempPath())).Path.TrimEnd('\')
        if ((Split-Path -Parent $resolvedTestRoot) -ne $resolvedTemp -or
                (Split-Path -Leaf $resolvedTestRoot) -notlike 'ae2lightoptimizer-pcl-preservation-test-*') {
            throw "Refusing to remove unexpected test path: $resolvedTestRoot"
        }
        Remove-Item -LiteralPath $resolvedTestRoot -Recurse -Force
    }
}
