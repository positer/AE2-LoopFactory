param(
    [string]$WorkspaceRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'

$versions = @(
    @{ Directory = 'neoforge-1.21.1'; ObjectIngredients = $true; Chest = 'ae2:chest' },
    @{ Directory = 'neoforge-26.1.2'; ObjectIngredients = $false; Chest = 'ae2:me_chest' }
)
$tiers = @('1k', '4k', '16k', '64k', '256k', '1m', '4m', '16m', '64m', '256m')

function Write-Json([string]$path, [object]$value) {
    New-Item -ItemType Directory -Path (Split-Path -Parent $path) -Force | Out-Null
    $json = $value | ConvertTo-Json -Depth 30
    [System.IO.File]::WriteAllText($path, $json + [Environment]::NewLine,
        [System.Text.UTF8Encoding]::new($false))
}

function Ingredient([string]$id, [bool]$objectIngredients) {
    if ($objectIngredients) { return [ordered]@{ item = $id } }
    return $id
}

function ShapedRecipe([string[]]$pattern, [System.Collections.IDictionary]$key, [string]$resultId) {
    return [ordered]@{
        type = 'minecraft:crafting_shaped'
        category = 'misc'
        pattern = $pattern
        key = $key
        result = [ordered]@{ id = $resultId; count = 1 }
    }
}

function ShapelessRecipe([object[]]$ingredients, [string]$resultId) {
    return [ordered]@{
        type = 'minecraft:crafting_shapeless'
        category = 'misc'
        ingredients = $ingredients
        result = [ordered]@{ id = $resultId; count = 1 }
    }
}

function ItemStack([string]$id) {
    return [ordered]@{ id = $id; count = 1 }
}

function DisassemblyRecipe([string]$cellId, [object[]]$items) {
    return [ordered]@{
        type = 'ae2:storage_cell_disassembly'
        cell = $cellId
        cell_disassembly_items = $items
    }
}

foreach ($version in $versions) {
    $recipeRoot = Join-Path $WorkspaceRoot `
        "versions\$($version.Directory)\src\main\resources\data\ae2lightoptimizer\recipe"
    $objects = $version.ObjectIngredients

    $housing = ShapedRecipe @('GPG', 'I I', 'GPG') ([ordered]@{
        G = Ingredient 'minecraft:glass' $objects
        P = Ingredient 'ae2lightoptimizer:loop_crystal_powder' $objects
        I = Ingredient 'minecraft:iron_ingot' $objects
    }) 'ae2lightoptimizer:loop_storage_cell_housing'
    Write-Json (Join-Path $recipeRoot 'loop_storage_cell_housing.json') $housing

    for ($index = 0; $index -lt $tiers.Count; $index++) {
        $tier = $tiers[$index]
        $coreId = "ae2lightoptimizer:$tier`_loop_storage_core"
        $cellId = "ae2lightoptimizer:$tier`_loop_storage_cell"

        if ($index -eq 0) {
            $coreRecipe = ShapedRecipe @('IFI', 'FCF', 'IFI') ([ordered]@{
                I = Ingredient 'minecraft:iron_ingot' $objects
                F = Ingredient 'ae2lightoptimizer:loop_crystal_fragment' $objects
                C = Ingredient 'ae2:certus_quartz_crystal' $objects
            }) $coreId
        } elseif ($index -le 4) {
            $previous = "ae2lightoptimizer:$($tiers[$index - 1])_loop_storage_core"
            $coreRecipe = ShapedRecipe @('IPI', 'PCP', 'IFI') ([ordered]@{
                I = Ingredient 'minecraft:iron_ingot' $objects
                P = Ingredient $previous $objects
                C = Ingredient 'ae2:certus_quartz_crystal' $objects
                F = Ingredient 'ae2lightoptimizer:loop_crystal_powder' $objects
            }) $coreId
        } else {
            $previous = "ae2lightoptimizer:$($tiers[$index - 1])_loop_storage_core"
            $coreRecipe = ShapedRecipe @('NPN', 'PSP', 'NLN') ([ordered]@{
                N = Ingredient 'minecraft:netherite_ingot' $objects
                P = Ingredient $previous $objects
                S = Ingredient 'ae2:singularity' $objects
                L = Ingredient 'ae2lightoptimizer:loop_crystal' $objects
            }) $coreId
        }
        Write-Json (Join-Path $recipeRoot "$tier`_loop_storage_core.json") $coreRecipe

        $cellRecipe = ShapedRecipe @('GPG', 'ICI', 'GPG') ([ordered]@{
            G = Ingredient 'minecraft:glass' $objects
            P = Ingredient 'ae2lightoptimizer:loop_crystal_powder' $objects
            I = Ingredient 'minecraft:iron_ingot' $objects
            C = Ingredient $coreId $objects
        }) $cellId
        Write-Json (Join-Path $recipeRoot "$tier`_loop_storage_cell.json") $cellRecipe

        $fromHousing = [ordered]@{
            type = 'minecraft:crafting_shapeless'
            category = 'misc'
            ingredients = @(
                (Ingredient $coreId $objects),
                (Ingredient 'ae2lightoptimizer:loop_storage_cell_housing' $objects)
            )
            result = [ordered]@{ id = $cellId; count = 1 }
        }
        Write-Json (Join-Path $recipeRoot "$tier`_loop_storage_cell_from_housing.json") $fromHousing

        $portableId = "ae2lightoptimizer:portable_$tier`_loop_storage_cell"
        $portableFromCell = ShapelessRecipe @(
            (Ingredient $version.Chest $objects),
            (Ingredient 'ae2:energy_cell' $objects),
            (Ingredient $cellId $objects)
        ) $portableId
        Write-Json (Join-Path $recipeRoot "portable_$tier`_loop_storage_cell.json") $portableFromCell

        $portableFromParts = ShapelessRecipe @(
            (Ingredient $version.Chest $objects),
            (Ingredient 'ae2:energy_cell' $objects),
            (Ingredient 'ae2lightoptimizer:loop_storage_cell_housing' $objects),
            (Ingredient $coreId $objects)
        ) $portableId
        Write-Json (Join-Path $recipeRoot "portable_$tier`_loop_storage_cell_from_parts.json") $portableFromParts

        $portableDisassembly = DisassemblyRecipe $portableId @(
            (ItemStack $version.Chest),
            (ItemStack 'ae2:energy_cell'),
            (ItemStack 'ae2lightoptimizer:loop_storage_cell_housing'),
            (ItemStack $coreId)
        )
        Write-Json (Join-Path $recipeRoot "portable_$tier`_loop_storage_cell_disassembly.json") `
            $portableDisassembly
    }

    $infiniteIngredients = [System.Collections.ArrayList]::new()
    for ($count = 0; $count -lt 64; $count++) {
        [void]$infiniteIngredients.Add((Ingredient 'ae2lightoptimizer:256m_loop_storage_core' $objects))
    }
    [void]$infiniteIngredients.Add((Ingredient 'ae2lightoptimizer:loop_storage_cell_housing' $objects))
    $infinite = [ordered]@{
        type = 'ae2:transform'
        circumstance = [ordered]@{ type = 'explosion' }
        ingredients = $infiniteIngredients
        result = [ordered]@{ id = 'ae2lightoptimizer:infinite_loop_storage_cell'; count = 1 }
    }
    Write-Json (Join-Path $recipeRoot 'infinite_loop_storage_cell.json') $infinite


    $portableInfinite = ShapelessRecipe @(
        (Ingredient $version.Chest $objects),
        (Ingredient 'ae2:energy_cell' $objects),
        (Ingredient 'ae2lightoptimizer:infinite_loop_storage_cell' $objects)
    ) 'ae2lightoptimizer:portable_infinite_loop_storage_cell'
    Write-Json (Join-Path $recipeRoot 'portable_infinite_loop_storage_cell.json') $portableInfinite

    $portableInfiniteDisassembly = DisassemblyRecipe `
        'ae2lightoptimizer:portable_infinite_loop_storage_cell' @(
            (ItemStack $version.Chest),
            (ItemStack 'ae2:energy_cell'),
            (ItemStack 'ae2lightoptimizer:infinite_loop_storage_cell')
        )
    Write-Json (Join-Path $recipeRoot 'portable_infinite_loop_storage_cell_disassembly.json') `
        $portableInfiniteDisassembly
}

Write-Output 'Generated 128 loop and portable-loop recipes for both maintained generations, including portable disassembly and exact 64-core AE2 explosion transforms.'
