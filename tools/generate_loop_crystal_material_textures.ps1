param()

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing.Common
Add-Type -AssemblyName System.IO.Compression.FileSystem

$workspace = Split-Path -Parent $PSScriptRoot
$ae2Jar = Get-ChildItem (Join-Path $workspace '.gradle-user-home\1.21.1\caches\modules-2\files-2.1\org.appliedenergistics\appliedenergistics2\19.2.17') -Recurse -Filter 'appliedenergistics2-19.2.17.jar' | Select-Object -First 1
if (-not $ae2Jar) { throw 'AE2 19.2.17 JAR not found.' }

function Read-ZipBitmap([string]$entryName) {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($ae2Jar.FullName)
    try {
        $entry = $zip.GetEntry($entryName)
        if (-not $entry) { throw "Missing AE2 texture: $entryName" }
        $stream = $entry.Open()
        try {
            $loaded = [System.Drawing.Bitmap]::FromStream($stream)
            return [System.Drawing.Bitmap]::new($loaded)
        } finally { $stream.Dispose() }
    } finally { $zip.Dispose() }
}

function Get-GrayRange([System.Drawing.Bitmap]$bitmap) {
    $min = 255
    $max = 0
    for ($y = 0; $y -lt $bitmap.Height; $y++) {
        for ($x = 0; $x -lt $bitmap.Width; $x++) {
            $pixel = $bitmap.GetPixel($x, $y)
            if ($pixel.A -eq 0) { continue }
            $gray = [int][Math]::Round(0.2126 * $pixel.R + 0.7152 * $pixel.G + 0.0722 * $pixel.B)
            $min = [Math]::Min($min, $gray)
            $max = [Math]::Max($max, $gray)
        }
    }
    return @($min, $max)
}

function Convert-ToLoopGray([System.Drawing.Bitmap]$source, [int]$targetMin, [int]$targetMax, [string[]]$outputs) {
    $range = Get-GrayRange $source
    $sourceMin = $range[0]
    $sourceSpan = [Math]::Max(1, $range[1] - $sourceMin)
    $result = [System.Drawing.Bitmap]::new($source.Width, $source.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    try {
        for ($y = 0; $y -lt $source.Height; $y++) {
            for ($x = 0; $x -lt $source.Width; $x++) {
                $pixel = $source.GetPixel($x, $y)
                if ($pixel.A -eq 0) { $result.SetPixel($x, $y, [System.Drawing.Color]::Transparent); continue }
                $gray = 0.2126 * $pixel.R + 0.7152 * $pixel.G + 0.0722 * $pixel.B
                $mapped = [int][Math]::Round($targetMin + (($gray - $sourceMin) / $sourceSpan) * ($targetMax - $targetMin))
                $mapped = [Math]::Max(0, [Math]::Min(255, $mapped))
                $result.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($pixel.A, $mapped, $mapped, $mapped))
            }
        }
        foreach ($output in $outputs) {
            $directory = Split-Path -Parent $output
            New-Item -ItemType Directory -Path $directory -Force | Out-Null
            $result.Save($output, [System.Drawing.Imaging.ImageFormat]::Png)
        }
    } finally { $result.Dispose() }
}

$designCrystal = [System.Drawing.Bitmap]::new('C:\Users\12252\Desktop\Files\Code\Minecraft Forge Workspace\design\loop_cristal.png')
try {
    $targetRange = Get-GrayRange $designCrystal
    $block = Read-ZipBitmap 'assets/ae2/textures/block/quartz_block.png'
    $powder = Read-ZipBitmap 'assets/ae2/textures/item/certus_quartz_dust.png'
    try {
        $versionRoots = @('neoforge-1.21.1', 'neoforge-26.1.2') | ForEach-Object { Join-Path $workspace "versions\$_\src\main\resources\assets\ae2lightoptimizer\textures" }
        Convert-ToLoopGray $block $targetRange[0] $targetRange[1] @($versionRoots | ForEach-Object { Join-Path $_ 'block\loop_crystal_block.png' })
        Convert-ToLoopGray $powder $targetRange[0] $targetRange[1] @($versionRoots | ForEach-Object { Join-Path $_ 'item\loop_crystal_powder.png' })
    } finally { $block.Dispose(); $powder.Dispose() }
} finally { $designCrystal.Dispose() }

Write-Output 'Generated loop crystal block/powder recolors from AE2 source textures without changing pixel geometry or alpha.'
