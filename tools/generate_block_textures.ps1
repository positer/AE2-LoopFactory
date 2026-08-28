param(
    [string]$WorkspaceRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

function Set-Pixel {
    param($Bitmap, [int]$X, [int]$Y, [string]$Color)
    $Bitmap.SetPixel($X, $Y, [System.Drawing.ColorTranslator]::FromHtml($Color))
}

function Fill-Rect {
    param($Bitmap, [int]$X0, [int]$Y0, [int]$X1, [int]$Y1, [string]$Color)
    for ($y = $Y0; $y -le $Y1; $y++) {
        for ($x = $X0; $x -le $X1; $x++) {
            Set-Pixel $Bitmap $x $y $Color
        }
    }
}

function New-MachineFace {
    $bitmap = [System.Drawing.Bitmap]::new(16, 16, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    Fill-Rect $bitmap 0 0 15 15 '#46515b'
    Fill-Rect $bitmap 1 1 14 14 '#9aa6ae'
    Fill-Rect $bitmap 2 2 13 13 '#303941'
    Fill-Rect $bitmap 3 3 12 12 '#151b21'
    foreach ($point in @(@(2,2), @(13,2), @(2,13), @(13,13))) {
        Set-Pixel $bitmap $point[0] $point[1] '#d6dde1'
    }
    foreach ($point in @(@(0,15), @(15,15), @(15,0))) {
        Set-Pixel $bitmap $point[0] $point[1] '#222a31'
    }
    return $bitmap
}

function New-RingTexture {
    param([bool]$Connected)
    $bitmap = New-MachineFace
    $dim = if ($Connected) { '#168f8a' } else { '#315456' }
    $bright = if ($Connected) { '#62f5df' } else { '#557779' }
    $core = if ($Connected) { '#bafff4' } else { '#263b3d' }
    for ($x = 5; $x -le 10; $x++) {
        Set-Pixel $bitmap $x 4 $dim
        Set-Pixel $bitmap $x 11 $dim
    }
    for ($y = 5; $y -le 10; $y++) {
        Set-Pixel $bitmap 4 $y $dim
        Set-Pixel $bitmap 11 $y $dim
    }
    foreach ($point in @(@(7,4), @(8,4), @(11,7), @(11,8), @(7,11), @(8,11), @(4,7), @(4,8))) {
        Set-Pixel $bitmap $point[0] $point[1] $bright
    }
    Fill-Rect $bitmap 6 6 9 9 '#202a30'
    foreach ($point in @(@(7,6), @(8,6), @(9,7), @(9,8), @(8,9), @(7,9), @(6,8), @(6,7))) {
        Set-Pixel $bitmap $point[0] $point[1] $core
    }
    return $bitmap
}

function New-SupercomputerTexture {
    param([bool]$Connected)
    $bitmap = [System.Drawing.Bitmap]::new(16, 16, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    Fill-Rect $bitmap 0 0 15 15 '#2a333b'
    Fill-Rect $bitmap 1 1 14 14 '#9aa6ae'
    Fill-Rect $bitmap 2 2 13 13 '#39434c'
    Fill-Rect $bitmap 3 3 12 12 '#151b21'
    foreach ($point in @(@(2,2), @(13,2), @(13,13), @(2,13))) {
        Set-Pixel $bitmap $point[0] $point[1] '#d6dde1'
    }

    $lane = if ($Connected) { '#32a9d8' } else { '#354e59' }
    $laneHighlight = if ($Connected) { '#7ee8ff' } else { '#50646d' }
    $pulse = if ($Connected) { '#ffc857' } else { '#5d553d' }
    $core = if ($Connected) { '#f7fbff' } else { '#59656d' }

    # Two horizontal and two vertical compute rails form a rotationally symmetric hash core.
    foreach ($point in @(
            @(6,3), @(6,4), @(6,5), @(6,6), @(6,7), @(6,8), @(6,9), @(6,10), @(6,11), @(6,12),
            @(9,3), @(9,4), @(9,5), @(9,6), @(9,7), @(9,8), @(9,9), @(9,10), @(9,11), @(9,12),
            @(3,6), @(4,6), @(5,6), @(7,6), @(8,6), @(10,6), @(11,6), @(12,6),
            @(3,9), @(4,9), @(5,9), @(7,9), @(8,9), @(10,9), @(11,9), @(12,9))) {
        Set-Pixel $bitmap $point[0] $point[1] $lane
    }
    foreach ($point in @(@(6,6), @(9,6), @(9,9), @(6,9))) {
        Set-Pixel $bitmap $point[0] $point[1] $laneHighlight
    }
    foreach ($point in @(@(6,3), @(9,3), @(12,6), @(12,9),
                         @(6,12), @(9,12), @(3,6), @(3,9))) {
        Set-Pixel $bitmap $point[0] $point[1] $pulse
    }
    foreach ($point in @(@(6,6), @(9,6), @(9,9), @(6,9))) {
        Set-Pixel $bitmap $point[0] $point[1] $core
    }
    return $bitmap
}

function Assert-RotationalSymmetry {
    param($Bitmap, [string]$Name)
    for ($y = 0; $y -lt 16; $y++) {
        for ($x = 0; $x -lt 16; $x++) {
            if ($Bitmap.GetPixel($x, $y).ToArgb() -ne $Bitmap.GetPixel(15 - $y, $x).ToArgb()) {
                throw "$Name is not invariant under a 90-degree rotation at ($x,$y)."
            }
        }
    }
}

function New-PointArray {
    param([object[]]$Coordinates)
    $points = [System.Drawing.Point[]]::new($Coordinates.Count)
    for ($index = 0; $index -lt $Coordinates.Count; $index++) {
        $points[$index] = [System.Drawing.Point]::new(
            [int]$Coordinates[$index][0], [int]$Coordinates[$index][1])
    }
    Write-Output -NoEnumerate $points
}

function New-RingBlockIcon {
    param($FaceTexture)

    $bitmap = [System.Drawing.Bitmap]::new(64, 64, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.Clear([System.Drawing.Color]::Transparent)
    $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceOver
    $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighSpeed
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None

    # DrawImage maps the square 16x16 source onto three contiguous isometric faces.
    # The texture faces themselves form the silhouette, with no added cube outline.
    [System.Drawing.Point[]]$topFace = New-PointArray @(@(32, 5), @(58, 19), @(6, 19))
    [System.Drawing.Point[]]$leftFace = New-PointArray @(@(6, 19), @(32, 33), @(6, 45))
    [System.Drawing.Point[]]$rightFace = New-PointArray @(@(32, 33), @(58, 19), @(32, 59))
    $sourceRectangle = [System.Drawing.Rectangle]::new(0, 0, 16, 16)
    $graphics.DrawImage($FaceTexture, $topFace, $sourceRectangle, [System.Drawing.GraphicsUnit]::Pixel)
    $graphics.DrawImage($FaceTexture, $leftFace, $sourceRectangle, [System.Drawing.GraphicsUnit]::Pixel)
    $graphics.DrawImage($FaceTexture, $rightFace, $sourceRectangle, [System.Drawing.GraphicsUnit]::Pixel)

    $topLight = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(38, 210, 245, 255))
    $leftShade = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(62, 0, 12, 18))
    $graphics.FillPolygon($topLight, (New-PointArray @(@(32, 5), @(58, 19), @(32, 33), @(6, 19))))
    $graphics.FillPolygon($leftShade, (New-PointArray @(@(6, 19), @(32, 33), @(32, 59), @(6, 45))))

    $leftShade.Dispose()
    $topLight.Dispose()
    $graphics.Dispose()

    # The icon border belongs to the square PNG viewport, not the cube silhouette.
    # Shrink the cube into a safe area, then draw a continuous four-sided frame.
    $framed = [System.Drawing.Bitmap]::new(64, 64, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $frameGraphics = [System.Drawing.Graphics]::FromImage($framed)
    $frameGraphics.Clear([System.Drawing.Color]::Transparent)
    $frameGraphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $frameGraphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    $frameGraphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None
    $frameGraphics.DrawImage(
        $bitmap,
        [System.Drawing.Rectangle]::new(6, 6, 52, 52),
        [System.Drawing.Rectangle]::new(0, 0, 64, 64),
        [System.Drawing.GraphicsUnit]::Pixel)

    $viewportLayers = @(
        @{ Inset = 0; Color = '#11181e' },
        @{ Inset = 1; Color = '#11181e' },
        @{ Inset = 2; Color = '#72838d' },
        @{ Inset = 3; Color = '#62f5df' },
        @{ Inset = 4; Color = '#26343c' }
    )
    foreach ($layer in $viewportLayers) {
        $minimum = [int]$layer.Inset
        $maximum = 63 - $minimum
        for ($offset = $minimum; $offset -le $maximum; $offset++) {
            Set-Pixel $framed $offset $minimum $layer.Color
            Set-Pixel $framed $offset $maximum $layer.Color
            Set-Pixel $framed $minimum $offset $layer.Color
            Set-Pixel $framed $maximum $offset $layer.Color
        }
    }

    $frameGraphics.Dispose()
    $bitmap.Dispose()
    return $framed
}

$textures = @{
    'recipe_ring_solver_terminal.png' = (New-RingTexture $false)
    'recipe_ring_solver_terminal_connected.png' = (New-RingTexture $true)
    'supercomputing_crafting_optimizer_interface.png' = (New-SupercomputerTexture $false)
    'supercomputing_crafting_optimizer_interface_connected.png' = (New-SupercomputerTexture $true)
}

Assert-RotationalSymmetry $textures['supercomputing_crafting_optimizer_interface.png'] 'Offline supercomputer texture'
Assert-RotationalSymmetry $textures['supercomputing_crafting_optimizer_interface_connected.png'] 'Connected supercomputer texture'

$modIcon = New-RingBlockIcon $textures['recipe_ring_solver_terminal_connected.png']

foreach ($version in @('neoforge-1.21.1', 'neoforge-26.1.2')) {
    $target = Join-Path $WorkspaceRoot "versions\$version\src\main\resources\assets\ae2lightoptimizer\textures\block"
    New-Item -ItemType Directory -Force -Path $target | Out-Null
    foreach ($entry in $textures.GetEnumerator()) {
        $entry.Value.Save((Join-Path $target $entry.Key), [System.Drawing.Imaging.ImageFormat]::Png)
    }
    $modIcon.Save(
        (Join-Path $WorkspaceRoot "versions\$version\src\main\resources\ae2lightoptimizer.png"),
        [System.Drawing.Imaging.ImageFormat]::Png)
}

$modIcon.Dispose()
foreach ($bitmap in $textures.Values) {
    $bitmap.Dispose()
}

Write-Output 'Generated four opaque 16x16 block textures and one bordered 64x64 isometric mod icon for both maintained versions.'
