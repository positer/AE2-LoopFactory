#requires -Version 5.1
param([Parameter(Mandatory=$true)][string]$EvidenceDir,
    [ValidatePattern('^[a-z0-9-]+$')][string]$BackupLabel = 'production-before-runtime-fixes')
$ErrorActionPreference = 'Stop'
$workspaceRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$versionsRoot = Join-Path $env:USERPROFILE 'Desktop/Files/Minecraft/PCL/.minecraft/versions'
$evidencePath = [IO.Path]::GetFullPath($EvidenceDir)
$targets = foreach ($generation in @('1.21.1','26.1.2')) {
    $instance = Join-Path $versionsRoot "AE2-lightoptimizer-$generation"
    $productionName = "ae2lightoptimizer-neoforge-mc$generation-0.0.4.jar"
    $probeName = "ae2lo-runtime-probe-mc$generation-1.jar"
    [pscustomobject]@{
        Generation=$generation; Instance=$instance
        ProductionSource=(Join-Path $workspaceRoot "versions/neoforge-$generation/build/libs/$productionName")
        ProductionTarget=(Join-Path $instance "mods/$productionName")
        ProbeSource=(Join-Path $PSScriptRoot "$generation/build/libs/$probeName")
        ProbeTarget=(Join-Path $instance "mods/$probeName")
        Backup=(Join-Path $evidencePath "$BackupLabel/$generation/$productionName")
    }
}
foreach ($target in $targets) {
    foreach ($file in @($target.ProductionSource,$target.ProductionTarget,$target.ProbeSource)) {
        if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw "Missing build/installation: $file" }
    }
    if (Test-Path -LiteralPath $target.Backup) { throw 'Backup already exists; inspect instead of overwriting it.' }
    foreach ($process in @(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'")) {
        if ($process.CommandLine -and ($process.CommandLine.Contains($target.Instance) -or
                ($process.CommandLine.Contains('runtime-ripper-acceptance') -and $process.CommandLine.Contains('java-arguments.txt')))) {
            throw "Close the active target/probe Java process before installing: $($process.ProcessId)"
        }
    }
}
foreach ($target in $targets) {
    New-Item -ItemType Directory -Path (Split-Path -Parent $target.Backup) -Force | Out-Null
    Copy-Item -LiteralPath $target.ProductionTarget -Destination $target.Backup
    if ((Get-FileHash -LiteralPath $target.Backup).Hash -ne (Get-FileHash -LiteralPath $target.ProductionTarget).Hash) {
        throw 'Old production backup hash mismatch'
    }
}
$installed = foreach ($target in $targets) {
    Copy-Item -LiteralPath $target.ProductionSource -Destination $target.ProductionTarget -Force
    Copy-Item -LiteralPath $target.ProbeSource -Destination $target.ProbeTarget -Force
    if ((Get-FileHash -LiteralPath $target.ProductionSource).Hash -ne (Get-FileHash -LiteralPath $target.ProductionTarget).Hash -or
            (Get-FileHash -LiteralPath $target.ProbeSource).Hash -ne (Get-FileHash -LiteralPath $target.ProbeTarget).Hash) {
        throw 'Installed build hash mismatch'
    }
    [pscustomobject]@{Generation=$target.Generation; Production=$target.ProductionTarget
        SHA256=(Get-FileHash -LiteralPath $target.ProductionTarget).Hash
        Probe=$target.ProbeTarget; ProbeSHA256=(Get-FileHash -LiteralPath $target.ProbeTarget).Hash; Backup=$target.Backup}
}
$installed | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $evidencePath 'installed-runtime-builds.json') -Encoding UTF8
$installed | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $evidencePath "installed-$BackupLabel.json") -Encoding UTF8
Write-Output 'Both production backups and installed production/probe SHA256 hashes verified.'
$installed | Select-Object Generation,SHA256
