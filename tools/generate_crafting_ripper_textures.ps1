param([string]$WorkspaceRoot = (Split-Path -Parent $PSScriptRoot))
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Pixel($Image, $X, $Y, $Hex) {
    $Image.SetPixel($X, $Y, [System.Drawing.ColorTranslator]::FromHtml($Hex))
}

# Extend the existing code-authored machine face. The outer three pixels retain
# its exact frame; nine discrete cells form a crafting grid in the core.
foreach ($generation in @('1.21.1', '26.1.2')) {
    $assets = Join-Path $WorkspaceRoot "versions/neoforge-$generation/src/main/resources/assets/ae2lightoptimizer"
    foreach ($connected in @($false, $true)) {
        $suffix = if ($connected) { '_connected' } else { '' }
        $source = [System.Drawing.Bitmap]::new((Join-Path $assets "textures/block/supercomputing_crafting_optimizer_interface$suffix.png"))
        $face = [System.Drawing.Bitmap]::new($source)
        for ($y = 3; $y -le 12; $y++) {
            for ($x = 3; $x -le 12; $x++) { Pixel $face $x $y '#151b21' }
        }
        $edge = if ($connected) { '#32a9d8' } else { '#354e59' }
        $fill = if ($connected) { '#7ee8ff' } else { '#50646d' }
        # A centred odd grid needs odd square cells: 3x3 2px cells with 1px gaps,
        # occupying coordinates 4..11, leaving a symmetric dark gutter.
        foreach ($y in @(4, 7, 10)) {
            foreach ($x in @(4, 7, 10)) {
                Pixel $face $x $y $fill
                Pixel $face ($x + 1) $y $edge
                Pixel $face $x ($y + 1) $edge
                Pixel $face ($x + 1) ($y + 1) $fill
            }
        }
        $face.Save((Join-Path $assets "textures/block/crafting_ripper$suffix.png"), [System.Drawing.Imaging.ImageFormat]::Png)
        $face.Dispose()
        $source.Dispose()
    }

    $version = if ($generation -eq '1.21.1') { '19.2.17' } else { '26.1.10-beta' }
    $cache = Join-Path $WorkspaceRoot ".gradle-user-home/$generation/caches/modules-2/files-2.1/org.appliedenergistics/appliedenergistics2/$version"
    $jar = Get-ChildItem $cache -Recurse -Filter "appliedenergistics2-$version.jar" | Select-Object -First 1
    if (-not $jar) { throw "Missing pinned AE2 $version JAR" }
    $zip = [System.IO.Compression.ZipFile]::OpenRead($jar.FullName)
    $entry = $zip.GetEntry('assets/ae2/textures/item/card_speed.png')
    if (-not $entry) { throw "Pinned AE2 card_speed.png missing" }
    $stream = $entry.Open()
    $reference = [System.Drawing.Bitmap]::new($stream)
    $card = [System.Drawing.Bitmap]::new($reference.Width, $reference.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    # Bitmap(Image) composites transparent pixels and discards their hidden RGB.
    # Copy raw ARGB explicitly so even the transparent source pixels are invariant.
    for ($y = 0; $y -lt $reference.Height; $y++) {
        for ($x = 0; $x -lt $reference.Width; $x++) {
            $card.SetPixel($x, $y, $reference.GetPixel($x, $y))
        }
    }
    # Native pixel-grid circular repeat emblem. Keep the native card
    # shell, contacts, status strip and all pixels outside its 7x7 face intact.
    $emblem = @('..####.', '.#...#.', '....###', '.......', '###....', '.#...#.', '.####..')
    for ($y = 0; $y -lt 7; $y++) {
        for ($x = 0; $x -lt 7; $x++) {
            $color = if ($emblem[$y][$x] -eq '#') { '#61666d' } else { '#a5a7ac' }
            Pixel $card ($x + 4) ($y + 3) $color
        }
    }
    $card.Save((Join-Path $assets 'textures/item/loop_card.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    $card.Dispose()
    $reference.Dispose()
    $stream.Dispose()
    $zip.Dispose()
    Write-Output "${generation}: exported two native 16x16 machine faces and original-shell Loop Card."
}
