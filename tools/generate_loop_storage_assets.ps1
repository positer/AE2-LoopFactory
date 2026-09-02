param(
    [string]$WorkspaceRoot = (Split-Path -Parent $PSScriptRoot),
    [string]$CoreSourceDir = ''
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

$java = (Get-Command java -ErrorAction Stop).Source
$javac = (Get-Command javac -ErrorAction Stop).Source
$recolorSource = Join-Path $PSScriptRoot 'StrictPngRecolor.java'
$temporaryRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
$recolorClasses = [System.IO.Path]::GetFullPath((Join-Path $temporaryRoot (
    'ae2lo-strict-recolor-' + [System.Guid]::NewGuid().ToString('N'))))
if (-not $recolorClasses.StartsWith($temporaryRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Unexpected recolor compiler path: $recolorClasses"
}
New-Item -ItemType Directory -Path $recolorClasses -Force | Out-Null
& $javac -d $recolorClasses $recolorSource
if ($LASTEXITCODE -ne 0) { throw 'Failed to compile StrictPngRecolor.java' }

if (-not $CoreSourceDir) {
    $CoreSourceDir = Join-Path (Split-Path -Parent $WorkspaceRoot) `
        'Minecraft Forge Workspace\design\process_core'
}

$tiers = @(
    @{ Id = '1k'; Source = '1k.png'; Base = '1k'; Theme = 'k'; Hash = '2EDF8D2CAC4A932F929494CA1A2001AEAD608CC878D44C6AA79F4B07DB1D0A50' },
    @{ Id = '4k'; Source = '4k.png'; Base = '4k'; Theme = 'k'; Hash = '65CAD27E4937581F852837F75B762F27501F3A30F924FA4FD3B92C620223F5EA' },
    @{ Id = '16k'; Source = '16k.png'; Base = '16k'; Theme = 'k'; Hash = '02E982D6A66297985BCDFE9EEFD640D057D9A9FDEFE33B8BA264142A9216B82A' },
    @{ Id = '64k'; Source = '64k.png'; Base = '64k'; Theme = 'k'; Hash = 'EE18B996A6F764DA19EEA5E16ECA29B5912C978DC407DCF9C95A4631BDDA4213' },
    @{ Id = '256k'; Source = '256k.png'; Base = '256k'; Theme = 'k'; Hash = '1F65958486B13DBD66B93F524ECEA5C471A12313E1525534C0BB0F1C68C8F6CC' },
    @{ Id = '1m'; Source = '1M.png'; Base = '1k'; Theme = 'm'; Hash = '9DB6AF7141F9A616E400076B67A18049443A772D655ABDA30239FCD94844364E' },
    @{ Id = '4m'; Source = '4M.png'; Base = '4k'; Theme = 'm'; Hash = '384663DD7616CDC0EFCA03BEAECA967FF5C59E6D5C81B4221E9A4F65BDC83B3F' },
    @{ Id = '16m'; Source = '16M.png'; Base = '16k'; Theme = 'm'; Hash = '1C0D5EE00D2DE66B12AD1491E6EC6F974FC2E4A50A85B5FAC7B85C030D498A8B' },
    @{ Id = '64m'; Source = '64M.png'; Base = '64k'; Theme = 'm'; Hash = '70A1B32233F4E21D98579205A30BE8726305EBBF18E6687A6CE35214DE9F04B0' },
    @{ Id = '256m'; Source = '256M.png'; Base = '256k'; Theme = 'm'; Hash = '8E63F6BE4F0E4DC2944F70002438E34216161923D65D6F3DD03DFE820EA42349' }
)

$versions = @(
    @{ Directory = 'neoforge-1.21.1'; Ae2Version = '19.2.17'; ModernItems = $false },
    @{ Directory = 'neoforge-26.1.2'; Ae2Version = '26.1.10-beta'; ModernItems = $true }
)

# Exact source RGB -> target RGB tables. Alpha is always copied unchanged.
$kShell = @{
    '65,63,84' = @(48, 48, 48); '77,77,103' = @(58, 58, 58)
    '105,109,136' = @(82, 82, 82); '135,143,165' = @(110, 110, 110)
    '154,159,180' = @(126, 126, 126); '173,176,196' = @(143, 143, 143)
    '203,204,212' = @(170, 170, 170); '222,223,227' = @(190, 190, 190)
    '242,242,242' = @(214, 214, 214)
}
$mShell = @{
    '65,63,84' = @(13, 13, 13); '77,77,103' = @(21, 21, 21)
    '105,109,136' = @(39, 39, 39); '135,143,165' = @(57, 57, 57)
    '154,159,180' = @(67, 67, 67); '173,176,196' = @(77, 77, 77)
    '203,204,212' = @(93, 93, 93); '222,223,227' = @(104, 104, 104)
    '242,242,242' = @(115, 115, 115)
}
$infiniteShell = @{
    '65,63,84' = @(22, 13, 29); '77,77,103' = @(30, 19, 41)
    '105,109,136' = @(43, 28, 58); '135,143,165' = @(57, 39, 76)
    '154,159,180' = @(68, 47, 89); '173,176,196' = @(80, 57, 104)
    '203,204,212' = @(100, 75, 127); '222,223,227' = @(119, 93, 147)
    '242,242,242' = @(145, 116, 174)
}
$infiniteCore = @{
    '45,105,225' = @(174, 118, 238)
    '56,148,255' = @(210, 160, 255)
    '64,193,255' = @(240, 214, 255)
}

function Get-Ae2Jar([hashtable]$version) {
    $artifactRoot = Join-Path $WorkspaceRoot ".gradle-user-home\$($version.Directory.Replace('neoforge-', ''))\caches\modules-2\files-2.1\org.appliedenergistics\appliedenergistics2\$($version.Ae2Version)"
    $jar = Get-ChildItem $artifactRoot -Recurse -File -Filter "appliedenergistics2-$($version.Ae2Version).jar" -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if (-not $jar) { throw "AE2 $($version.Ae2Version) JAR not found under $artifactRoot" }
    return $jar.FullName
}

function Read-ZipBytes([string]$jarPath, [string]$entryName) {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
    try {
        $entry = $zip.GetEntry($entryName)
        if (-not $entry) { throw "Missing $entryName in $jarPath" }
        $stream = $entry.Open()
        try {
            $memory = [System.IO.MemoryStream]::new()
            $stream.CopyTo($memory)
            return $memory.ToArray()
        } finally { $stream.Dispose() }
    } finally { $zip.Dispose() }
}

function Write-DriveModel(
        [string]$jarPath,
        [bool]$modernItems,
        [string]$baseTier,
        [string]$path) {
    $entry = if ($modernItems) {
        "assets/ae2/models/block/drive_$baseTier`_item_cell.json"
    } else {
        "assets/ae2/models/block/drive/cells/$baseTier`_item_cell.json"
    }
    $directory = Split-Path -Parent $path
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    [System.IO.File]::WriteAllBytes($path, (Read-ZipBytes $jarPath $entry))
}

function Write-Utf8([string]$path, [string]$content) {
    $directory = Split-Path -Parent $path
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    [System.IO.File]::WriteAllText($path, $content + [Environment]::NewLine,
        [System.Text.UTF8Encoding]::new($false))
}

function Write-ZipEntry([string]$jarPath, [string]$entryName, [string]$path) {
    $directory = Split-Path -Parent $path
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    [System.IO.File]::WriteAllBytes($path, (Read-ZipBytes $jarPath $entryName))
}

function Merge-Lut([hashtable]$first, [hashtable]$second) {
    $merged = @{}
    foreach ($entry in $first.GetEnumerator()) { $merged[$entry.Key] = $entry.Value }
    foreach ($entry in $second.GetEnumerator()) { $merged[$entry.Key] = $entry.Value }
    return $merged
}

function Write-StrictRecolor(
        [string]$jarPath,
        [string]$entryName,
        [hashtable]$lut,
        [string]$output,
        [bool]$requireEveryOpaquePixelMapped) {
    $encodedLut = ($lut.GetEnumerator() | Sort-Object Key | ForEach-Object {
        "$($_.Key)=$($_.Value -join ',')"
    }) -join ';'
    & $java -cp $recolorClasses StrictPngRecolor `
        $jarPath $entryName $output $requireEveryOpaquePixelMapped.ToString().ToLowerInvariant() $encodedLut
    if ($LASTEXITCODE -ne 0) { throw "Strict recolor failed for $output" }
}

function Write-ItemModel([string]$resourceRoot, [string]$id, [bool]$modernItems, [bool]$storageCell) {
    $textureLayers = @"
    "layer0": "ae2lightoptimizer:item/$id"
"@
    if ($storageCell) {
        $textureLayers = @"
    "layer0": "ae2lightoptimizer:item/$id",
    "layer1": "ae2:item/storage_cell_led"
"@
    }
    $model = @"
{
  "parent": "minecraft:item/generated",
  "textures": {
$textureLayers
  }
}
"@
    Write-Utf8 (Join-Path $resourceRoot "assets\ae2lightoptimizer\models\item\$id.json") $model.TrimEnd()
    if ($modernItems) {
        $tints = ''
        if ($storageCell) {
            $tints = @"
,
    "tints": [
      { "type": "minecraft:constant", "value": -1 },
      { "type": "ae2:storage_cell_state" }
    ]
"@
        }
        $descriptor = @"
{
  "model": {
    "type": "minecraft:model",
    "model": "ae2lightoptimizer:item/$id"$tints
  }
}
"@
        Write-Utf8 (Join-Path $resourceRoot "assets\ae2lightoptimizer\items\$id.json") $descriptor.TrimEnd()
    }
}

function Write-PortableItemModel(
        [string]$resourceRoot,
        [string]$id,
        [string]$housingTexture,
        [string]$sideTexture,
        [bool]$modernItems) {
    $model = @"
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "ae2lightoptimizer:item/$housingTexture",
    "layer1": "ae2:item/portable_cell_led",
    "layer2": "ae2:item/portable_cell_screen",
    "layer3": "ae2lightoptimizer:item/$sideTexture"
  }
}
"@
    Write-Utf8 (Join-Path $resourceRoot "assets\ae2lightoptimizer\models\item\$id.json") $model.TrimEnd()
    if ($modernItems) {
        $descriptor = @"
{
  "model": {
    "type": "minecraft:model",
    "model": "ae2lightoptimizer:item/$id",
    "tints": [
      { "type": "minecraft:constant", "value": -1 },
      { "type": "ae2:storage_cell_state" },
      { "type": "ae2:portable_cell_color" }
    ]
  }
}
"@
        Write-Utf8 (Join-Path $resourceRoot "assets\ae2lightoptimizer\items\$id.json") $descriptor.TrimEnd()
    }
}

try {
foreach ($version in $versions) {
    $jarPath = Get-Ae2Jar $version
    $resourceRoot = Join-Path $WorkspaceRoot "versions\$($version.Directory)\src\main\resources"
    $textureRoot = Join-Path $resourceRoot 'assets\ae2lightoptimizer\textures\item'

    Write-StrictRecolor $jarPath 'assets/ae2/textures/item/item_cell_housing.png' `
        $kShell (Join-Path $textureRoot 'loop_storage_cell_housing.png') $true
    Write-ItemModel $resourceRoot 'loop_storage_cell_housing' $version.ModernItems $false

    Write-StrictRecolor $jarPath 'assets/ae2/textures/item/portable_cell_item_housing.png' `
        $kShell (Join-Path $textureRoot 'portable_loop_storage_cell_housing_k.png') $true
    Write-StrictRecolor $jarPath 'assets/ae2/textures/item/portable_cell_item_housing.png' `
        $mShell (Join-Path $textureRoot 'portable_loop_storage_cell_housing_m.png') $true
    Write-StrictRecolor $jarPath 'assets/ae2/textures/item/portable_cell_item_housing.png' `
        $infiniteShell (Join-Path $textureRoot 'portable_loop_storage_cell_housing_infinite.png') $true

    foreach ($tier in $tiers) {
        $sourceCore = Join-Path $CoreSourceDir $tier.Source
        if (-not (Test-Path -LiteralPath $sourceCore -PathType Leaf)) {
            throw "Missing immutable core texture $sourceCore"
        }
        $sourceHash = (Get-FileHash -LiteralPath $sourceCore -Algorithm SHA256).Hash
        if ($sourceHash -ne $tier.Hash) {
            throw "Immutable source hash mismatch for $($tier.Source): $sourceHash"
        }

        $coreId = "$($tier.Id)_loop_storage_core"
        $cellId = "$($tier.Id)_loop_storage_cell"
        $targetCore = Join-Path $textureRoot "$coreId.png"
        New-Item -ItemType Directory -Path (Split-Path -Parent $targetCore) -Force | Out-Null
        [System.IO.File]::Copy($sourceCore, $targetCore, $true)
        $targetHash = (Get-FileHash -LiteralPath $targetCore -Algorithm SHA256).Hash
        if ($targetHash -ne $sourceHash) { throw "Core copy was not byte-identical for $coreId" }

        $themeLut = if ($tier.Theme -eq 'k') { $kShell } else { $mShell }
        Write-StrictRecolor $jarPath "assets/ae2/textures/item/item_storage_cell_$($tier.Base).png" `
            $themeLut (Join-Path $textureRoot "$cellId.png") $false

        Write-ItemModel $resourceRoot $coreId $version.ModernItems $false
        Write-ItemModel $resourceRoot $cellId $version.ModernItems $true

        $portableId = "portable_$($tier.Id)_loop_storage_cell"
        $portableSide = "$portableId`_side"
        Write-ZipEntry $jarPath "assets/ae2/textures/item/portable_cell_side_$($tier.Base).png" `
            (Join-Path $textureRoot "$portableSide.png")
        $portableHousing = if ($tier.Theme -eq 'k') {
            'portable_loop_storage_cell_housing_k'
        } else {
            'portable_loop_storage_cell_housing_m'
        }
        Write-PortableItemModel $resourceRoot $portableId $portableHousing $portableSide $version.ModernItems

        $driveModelPath = Join-Path $resourceRoot "assets\ae2lightoptimizer\models\block\drive\cells\$cellId.json"
        Write-DriveModel $jarPath $version.ModernItems $tier.Base $driveModelPath
    }

    Write-StrictRecolor $jarPath 'assets/ae2/textures/item/item_storage_cell_1k.png' `
        (Merge-Lut $infiniteShell $infiniteCore) `
        (Join-Path $textureRoot 'infinite_loop_storage_cell.png') $true
    Write-ItemModel $resourceRoot 'infinite_loop_storage_cell' $version.ModernItems $true

    Write-StrictRecolor $jarPath 'assets/ae2/textures/item/portable_cell_side_1k.png' `
        $infiniteCore (Join-Path $textureRoot 'portable_infinite_loop_storage_cell_side.png') $true
    Write-PortableItemModel $resourceRoot 'portable_infinite_loop_storage_cell' `
        'portable_loop_storage_cell_housing_infinite' `
        'portable_infinite_loop_storage_cell_side' $version.ModernItems

    $infiniteDrivePath = Join-Path $resourceRoot `
        'assets\ae2lightoptimizer\models\block\drive\cells\infinite_loop_storage_cell.json'
    Write-DriveModel $jarPath $version.ModernItems '1k' $infiniteDrivePath
}
} finally {
    if (Test-Path -LiteralPath $recolorClasses) {
        Remove-Item -LiteralPath $recolorClasses -Recurse -Force
    }
}

Write-Output 'Generated loop and portable-loop assets: immutable cores, strict LUT housings/cells, native portable layers, item descriptors, and native AE2 drive models.'
