param(
    [Parameter(Mandatory=$true)][ValidateSet('1.21.1','26.1.2')][string]$Generation,
    [Parameter(Mandatory=$true)][ValidateSet('omni','omni_access','ae2lt_capacity','factory_wireless','template','channel_capacity','smithing','layered','core256m','catalog_audit','native_catalog','exact_components','pack_catalog','tool_components')][string]$Chain,
    [Parameter(Mandatory=$true)][ValidatePattern('^ripper-[A-Za-z0-9_.-]+$')][string]$EvidenceName,
    [string]$EvidenceRoot,
    [switch]$PrepareOnly,
    [switch]$ValidateExisting,
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9_.-]*$')][string]$ValidationName
)
$ErrorActionPreference = 'Stop'
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$projectRoot = Join-Path $taskRoot "versions/neoforge-$Generation"
$runRoot = Join-Path $projectRoot 'run'
$runMods = [IO.Path]::GetFullPath((Join-Path $runRoot 'mods'))
$resolvedEvidenceRoot = if ($EvidenceRoot) { [IO.Path]::GetFullPath($EvidenceRoot) } else { Join-Path $taskRoot 'archive/2026-09-12-expanded-background-qa' }
$evidence = [IO.Path]::GetFullPath((Join-Path $resolvedEvidenceRoot $EvidenceName))
$helper = Join-Path $PSScriptRoot "$Generation/build/libs/ae2lo-runtime-probe-mc$Generation-1.jar"
$agent = Join-Path $taskRoot 'tools/runtime-factory-probe/background-agent/hidden-window-agent.jar'
$versionLine = @(Get-Content -LiteralPath (Join-Path $projectRoot 'gradle.properties') | Where-Object { $_ -match '^mod_version=' })
if ($versionLine.Count -ne 1) { throw 'Expected one mod_version in the generation properties' }
$modVersion = ($versionLine[0] -split '=',2)[1].Trim()
$production = Join-Path $projectRoot "build/libs/ae2lf-neoforge-mc$Generation-$modVersion.jar"

function Assert-WorkspacePath([string]$Path) {
    $resolved = [IO.Path]::GetFullPath($Path)
    if (-not $resolved.StartsWith($taskRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing path outside this workspace: $resolved"
    }
}
function Get-Sha256([string]$Path) { (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() }
function Write-EvidenceJson([string]$Name, $Value) {
    [IO.File]::WriteAllText((Join-Path $evidence $Name), ($Value | ConvertTo-Json -Depth 20), (New-Object Text.UTF8Encoding($false)))
}
function Get-ModSnapshot {
    if (Test-Path -LiteralPath $runMods) {
        Get-ChildItem -LiteralPath $runMods -File -Recurse | Sort-Object FullName | ForEach-Object {
            [ordered]@{name=$_.FullName.Substring($runMods.Length + 1);bytes=$_.Length;sha256=(Get-Sha256 $_.FullName)}
        }
    }
}
function Read-PassingFixture([string]$Name) {
    $report = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $evidence $Name) | ConvertFrom-Json
    if ($report.passed -ne $true) { throw "Independent fixture did not pass: $Name" }
    return $report
}
function Assert-HelperMatchesProduction {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [IO.Compression.ZipFile]::OpenRead($helper)
    try {
        $metadata = $archive.GetEntry('META-INF/neoforge.mods.toml')
        if ($null -eq $metadata) { throw 'Ripper helper is missing its NeoForge metadata' }
        $reader = New-Object IO.StreamReader($metadata.Open(), [Text.Encoding]::UTF8)
        try { $toml = $reader.ReadToEnd() } finally { $reader.Dispose() }
        $blocks = [regex]::Matches($toml, '(?ms)^\[\[dependencies\.ae2lo_runtime_probe\]\][ \t]*\r?\n(?<body>.*?)(?=^\[\[|\z)')
        $productionDependency = @($blocks | Where-Object {
            $_.Groups['body'].Value -match '(?m)^modId\s*=\s*"ae2lightoptimizer"\s*$'
        })
        if ($productionDependency.Count -ne 1) { throw 'Expected exactly one production dependency in the actual helper JAR' }
        $range = [regex]::Match($productionDependency[0].Groups['body'].Value, '(?m)^versionRange\s*=\s*"([^"]+)"\s*$')
        $expected = '[' + $modVersion + ']'
        if (-not $range.Success -or $range.Groups[1].Value -ne $expected) {
            throw "Stale/incompatible ripper helper: expected production dependency $expected, found '$($range.Groups[1].Value)'. Rebuild ripperProbeJar before starting Java."
        }
        return [ordered]@{productionDependency=$range.Groups[1].Value;testedVersion=$modVersion}
    } finally { $archive.Dispose() }
}
function Assert-RuntimeMatchesProduction {
    # ModDev runClient loads the main source set. Verify those exact bytes against the frozen JAR.
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [IO.Compression.ZipFile]::OpenRead($production)
    $classCount = 0
    $entryCount = 0
    try {
        foreach ($entry in $archive.Entries) {
            $name = $entry.FullName
            if ($name.EndsWith('/') -or $name -eq 'META-INF/MANIFEST.MF') { continue }
            if ($name -match '(^com/example/(ae2loprobe|ae2lfprobe)/|\.jar$)') { throw "Unexpected helper/nested JAR in production: $name" }
            if ($name.EndsWith('.class')) {
                $candidate = Join-Path $projectRoot ('build/classes/java/main/' + $name)
                $classCount++
            } else { $candidate = Join-Path $projectRoot ('build/resources/main/' + $name) }
            if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) { throw "Production entry missing from the runtime source set: $name" }
            $stream = $entry.Open()
            $digest = [Security.Cryptography.SHA256]::Create()
            try { $jarHash = ([BitConverter]::ToString($digest.ComputeHash($stream))).Replace('-','').ToLowerInvariant() }
            finally { $digest.Dispose(); $stream.Dispose() }
            if ((Get-Sha256 $candidate) -ne $jarHash) { throw "Runtime differs from the frozen production JAR: $name" }
            $entryCount++
        }
        $runtimeClasses = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'build/classes/java/main') -Filter '*.class' -File -Recurse)
        if ($classCount -eq 0 -or $runtimeClasses.Count -ne $classCount) { throw 'Production/runtime class inventories differ' }
        return [ordered]@{classCount=$classCount;entryCount=$entryCount;mode='ModDev main source set, byte-matched against every production JAR entry except the generated manifest'}
    } finally { $archive.Dispose() }
}

function Assert-GameplayEvidence([string]$world) {
    $report = Read-PassingFixture 'report.json'
    if ($report.minecraft -ne $Generation -or $report.world -ne $world) { throw 'Report generation/world is not this run' }
    $chainNames = @{omni='omni_connector';omni_access='omni_access';ae2lt_capacity='ae2lt_capacity';factory_wireless='factory_wireless';channel_capacity='channel_capacity';template='template_growth';smithing='template_growth_then_netherite_pickaxe_smithing';layered='logs_planks_sticks_tool_and_template_growth_then_smithing';core256m='all_ten_core_tiers_crystal_growth_and_netherite_ingots';native_catalog='native_catalog_map_extension';exact_components='exact_components';catalog_audit='catalog_audit';pack_catalog='pack_catalog';tool_components='tool_components'}
    if ($report.chain -ne $chainNames[$Chain]) { throw 'The requested chain is not the completed fixture' }
    # 0.0.6 removed the ripper's own idle drain; older evidence keeps its recorded 5 AE/t.
    if ($Chain -notin @('omni','omni_access','ae2lt_capacity','factory_wireless') -and $report.ripperConfiguredIdleAEPerTick -ne 0) { throw 'Ripper idle drain is not zero' }
    if ($Chain -eq 'omni_access' -and $report.ripperIdlePower -ne 0) { throw 'Ripper idle drain is no longer zero' }
    if ($Chain -eq 'omni') {
        foreach ($check in @('four_loaded_endpoints','one_star_hub','connected_without_power','connectors_require_no_channels',
                'different_links_isolated','old_link_membership_removed','reselect_merges_native_grids',
                'baseline_connector_capacity_32','star_native_channel_limit_32','machine_relay_baseline_limited',
                'supercomputer_connector_capacity_64','supercomputer_machine_relay_capacity_16',
                'cross_dimension_40_channels_active','machine_relay_12_consumers_active',
                'removed_supercomputer_restores_32','removed_supercomputer_restores_machine_limit',
                'destroy_endpoint_removes_virtual_edge','replaced_endpoint_rejoins','omni_nodes_idle_zero',
                'break_test_pair_linked','linked_connector_breaks_in_one_hit','linked_connector_block_entity_removed','break_test_removes_virtual_edge',
                'omni_nodes_and_virtual_edges_no_channel_energy','native_shapeless_recipe_produces_four',
                'native_wrench_dismantles','wrench_returns_connector_item','wrench_item_replacement_preserves_link',
                'white_sprite_loaded','white_sprite_no_colored_pixels','unloaded_endpoint_old_node_destroyed','actual_chunk_unload_detaches',
                'chunk_reload_preserves_binding','chunk_reload_rejoins_star','native_client_create_sync','online_flag_matches_native_zero_power_state','powered_connected_connector_online',
                'no_server_particle_tick','client_particle_tick_registered','particle_online_state_synchronized','native_white_particle_provider','particle_inside_single_block',
                'particle_slow_outward_lifetime','particle_removed_before_leaving_block','production_online_animation_emits',
                'unlinked_particle_state_synchronized','offline_animation_emits_nothing','existing_particle_stops_when_disconnected')) {
            if ($report.$check -ne $true) { throw "Missing native connector contract: $check" }
        }
        foreach ($direction in @('down','up','north','south','west','east')) {
            if ($report.("native_shape_$direction") -ne $true) { throw "Missing connector shape: $direction" }
            foreach ($water in @('false','true')) {
                foreach ($kind in @('placement_facing','placement_water','native_collision')) {
                    $key = "${kind}_${direction}_$water"
                    if ($report.$key -ne $true) { throw "Missing native placement contract: $key" }
                }
            }
        }
        foreach ($frame in @('ui','invalid','created','page2','unlinked','restored','zh-1x','zh-2x','en-2x','world','particles-online','particles-offline')) {
            if (-not (Test-Path -LiteralPath (Join-Path $evidence "screenshots/ae2lo-ripper-omni-$frame.png"))) { throw "Missing native connector frame: $frame" }
        }
        $diskResult = & python (Join-Path $taskRoot 'tools/verify_omni_saved_data.py') $evidence --generation $Generation
        if ($LASTEXITCODE -ne 0) { throw 'Connector disk NBT readback failed' }
        $null = Read-PassingFixture 'saved-data-readback.json'
    }
    if ($Chain -eq 'factory_wireless') {
        foreach ($check in @('terminal_structure_joins_channel_factory_network','terminal_structure_sets_wireless_state',
                'terminal_structure_merges_ae_main_grid','terminal_chunk_is_factory_network_member',
                'removed_terminal_drops_membership','removed_terminal_clears_wireless_state','removed_terminal_clears_membership',
                'provider_subnet_face_points_up','provider_structure_joins_channel_factory_network',
                'provider_structure_sets_wireless_state','provider_structure_merges_ae_main_grid',
                'provider_subnet_is_factory_network_member','provider_subnet_is_not_on_the_ae_channel',
                'provider_main_network_is_not_broadcast','removed_provider_drops_membership',
                'removed_provider_clears_wireless_state','network_a_reaches_its_own_chunk',
                'network_b_reaches_the_shared_chunk','shared_machine_is_a_member_of_network_a',
                'shared_machine_is_a_member_of_network_b','terminals_are_not_wirelessly_joined_from_a',
                'terminals_are_not_wirelessly_joined_from_b','network_a_does_not_reach_network_b_only_chunk',
                'network_b_reaches_its_second_chunk','network_a_tag_binds_the_shared_member',
                'network_b_tag_binds_the_same_member','network_a_cannot_tag_network_b_only_chunk',
                'tagging_alone_moves_nothing','network_a_resolves_its_tagged_member',
                'network_b_resolves_its_tagged_member','tag_stores_are_per_network',
                'one_network_tag_does_not_leak','removed_connector_drops_that_network_membership',
                'other_network_membership_is_untouched')) {
            if ($report.$check -ne $true) { throw "Missing factory wireless contract: $check" }
        }
    }
    if ($Chain -eq 'omni_access') {
        foreach ($check in @('public_and_private_ids_differ','same_private_name_is_per_owner','owner_sees_public_and_own_private',
                'other_sees_public_and_their_private','private_link_invisible_to_other_player','public_link_visible_to_other_player',
                'other_owner_sees_own_private_link','private_link_binds_owner_connector','owner_second_endpoint_joins_private_link',
                'private_link_is_one_native_grid','connector_menu_opened','menu_refuses_invisible_private_link','menu_refuses_other_private_link',
                'menu_accepts_public_link','menu_restores_own_private_link','menu_creates_private_link','created_private_link_owner_is_player',
                'created_private_link_invisible_to_other','public_name_join_is_idempotent','ripper_placed','ripper_node_ready',
                'ripper_has_no_idle_power','ripper_online_without_own_power','probe_pattern_is_supported','third_party_insert_accepted',
                'third_party_insert_landed_in_requested_slot','insert_grew_the_pattern_tail','inserted_pattern_is_advertised',
                'unsupported_item_returned_not_dropped','unsupported_item_not_stored')) {
            if ($report.$check -ne $true) { throw "Missing channel visibility/ripper contract: $check" }
        }
        $diskResult = & python (Join-Path $taskRoot 'tools/verify_omni_saved_data.py') $evidence --generation $Generation
        if ($LASTEXITCODE -ne 0) { throw 'Connector disk NBT readback failed' }
        $null = Read-PassingFixture 'saved-data-readback.json'
    }
    $independentReports = @{
        exact_components=@('exact-components-report.json');native_catalog=@('native-catalog-report.json','native-continuation-roundtrip.json');
        pack_catalog=@('pack-catalog-report.json');tool_components=@('tool-components-report.json');catalog_audit=@('catalog-report.json')
    }
    if ($independentReports.ContainsKey($Chain)) {
        foreach ($file in $independentReports[$Chain]) { $null = Read-PassingFixture $file }
    }
    if ($Chain -eq 'template' -or $Chain -eq 'channel_capacity') {
        $null = Read-PassingFixture 'dynamic-patterns-report.json'
        $null = Read-PassingFixture 'dynamic-pattern-ui.json'
        $import = Read-PassingFixture 'pattern-import-report.json'
        if ($import.expectedMoved -ne 309 -or $import.maxTransferredPerTick -gt 128 -or
            $import.assemblerPhysicalPatternOnly -ne $true -or $import.exactComponentsConserved -ne $true -or $import.resumeConserved -ne $true -or
            $import.closeCancelsUncommittedWork -ne $true -or $import.ui.offlineClickRejected -ne $true) {
            throw 'Native pattern import/conservation/UI contract failed'
        }
        foreach ($state in @('en-normal-1x','en-hover-1x','zh-hover-1x','zh-complete-1x','en-hover-2x','running-2x','offline-2x','zh-hover-2x','restored')) {
            if (-not (Test-Path -LiteralPath (Join-Path $evidence ("screenshots/ae2lo-ripper-import-$state.png")))) {
                throw "Missing pattern import UI frame: $state"
            }
        }
        foreach ($frame in @('ae2lo-ripper-bottom.png','ae2lo-ripper-after-bulk.png','ae2lo-ripper-pattern-access-high-slot.png')) {
            if (-not (Test-Path -LiteralPath (Join-Path $evidence ('screenshots/' + $frame)))) { throw "Missing dynamic native frame: $frame" }
        }
    }
    if ($Chain -eq 'channel_capacity') {
        $capacity = Read-PassingFixture 'channel-capacity-report.json'
        $requiredCapacityChecks=@('baseline_dense_32_active','baseline_adhoc_15_rejected','dense_40_active_over_old_face_limit','adhoc_16_including_optimizer_active','all_six_faces_verified','native_smart_visual_normalizes_40_of_64_to_20_of_32','adhoc_17_rejected_without_boost_oscillation','adhoc_recovers_after_excess_load_removed','configured_x2_mode_is_doubled_not_replaced','infinite_mode_unchanged','duplicate_optimizers_do_not_stack','removing_optimizer_restores_face_and_dense_limits','removing_optimizer_restores_adhoc_limit','reinstalling_optimizer_repaths_both_networks','split_load_grids_do_not_keep_stale_capacity','merged_network_restores_capacity')
        foreach ($check in $requiredCapacityChecks) { if ($capacity.$check -ne $true) {throw "Missing native capacity check: $check"} }
        foreach ($index in 0..5) {if ($capacity.("controller_face_${index}_64") -ne $true) {throw 'Not all six controller faces passed'}}
        foreach ($index in @(1,2,7,8,12)) {if ($capacity.("independent_grid_unchanged_phase_$index") -ne $true) {throw 'Independent network isolation failed'}}
        $crafting=Read-PassingFixture 'dynamic-patterns-report.json'
        if ($capacity.duplicateStableServerTicks -lt 60 -or $crafting.originalOptimizerCpuOwnership -ne $true -or $report.channelOptimizerOnline -ne $true) {throw 'Stable duplicate election or original optimizer CPU ownership failed'}
    }
    if ($Chain -eq 'core256m') {
        if ($report.scheduleOwner -ne 'RING_TERMINAL' -or $report.bothPlannerServicesOnline -ne $true -or
            $report.run1.passed -ne $true -or $report.run1.nativeCpuTicks -ne 1 -or $report.run1.ripperCalls -ne 1 -or
            $report.run1.isolatedExecutionAE -ne 0 -or $report.run1.sameExecutionTick -ne $true -or
            $report.run1.after.core_256m -ne 3000 -or $report.run1.after.loop_crystal -ne 1) {
            throw 'Core256M native CPU/fee/seed/output contract failed'
        }
    }
    if ($Chain -eq 'exact_components') {
        $exact = Read-PassingFixture 'exact-components-report.json'
        if (@($exact.runs).Count -ne 2 -or @($exact.runs | Where-Object {
            $_.passed -ne $true -or $_.outputFullKeyCount -ne 3000 -or $_.nativeCpuCalls -ne 1 -or
            $_.ripperCalls -ne 1 -or $_.executionFeeAE -ne 0 -or $_.nativeCpuFinished -ne $true -or $_.fullInventoryExact -ne $true
        }).Count -ne 0 -or $exact.runs[0].mode -ne 'manual' -or $exact.runs[1].mode -ne 'loop_card') {
            throw 'Both 3000-copy exact component orders must pass'
        }
    }
    if ($Chain -eq 'native_catalog') {
        $native = Read-PassingFixture 'native-catalog-report.json'
        $continuation = Read-PassingFixture 'native-continuation-roundtrip.json'
        if ($native.validNewScaledMapCount -ne 3 -or $native.nativeCpuIdle -ne $true -or $native.nativeMultiTickProgressObserved -ne $true -or
            $native.remainingSourceMaps -ne 0 -or $native.remainingPaper -ne 0 -or
            $continuation.roundTripPerformed -ne $true -or $continuation.cpuRoundTripPerformed -ne $true -or
            $continuation.completeCpuRoundTrip.passed -ne $true -or
            $continuation.totalExecutionFeeAE -ne 0 -or -not (Test-Path -LiteralPath (Join-Path $evidence 'native-cpu-first-commit.nbt'))) {
            throw 'Native map identities, CPU persistence or one-time fee contract failed'
        }
    }
    $requiredFrames = if ($Chain -eq 'omni') { @('ae2lo-ripper-omni-ui.png','ae2lo-ripper-omni-world.png') }
        elseif ($Chain -eq 'omni_access') { @('ae2lo-ripper-omni-access-ui.png','ae2lo-ripper-omni-access-private-toggle.png','ae2lo-ripper-omni-access-private-created.png','ae2lo-ripper-omni-access-zh-2x.png','ae2lo-ripper-omni-access-en-2x.png') }
        elseif ($Chain -eq 'ae2lt_capacity' -or $Chain -eq 'factory_wireless') { @() }
        else { @('ae2lo-ripper-after.png','ae2lo-ripper-ui.png') }
    foreach ($frame in $requiredFrames) {
        $framePath = Join-Path $evidence ('screenshots/' + $frame)
        if (-not (Test-Path -LiteralPath $framePath -PathType Leaf) -or (Get-Item -LiteralPath $framePath).Length -le 8) { throw "Missing native framebuffer: $frame" }
    }
    $hidden = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $evidence 'hidden-window-state.json') | ConvertFrom-Json
    if ($Generation -eq '1.21.1' -and $hidden.shutdownBudgetHookApplied -ne $true) {
        throw 'The production native shutdown Mixin did not transform the actual MinecraftServer class'
    }
    if ($hidden.clientTicksChecked -le 0 -or $hidden.visibleObservations -ne 0 -or $hidden.lastWindowVisible -ne 0 -or $hidden.normalStopObserved -ne $true) {
        throw 'Hidden-window observation or final normal-stop evidence failed'
    }
    $shutdown = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $evidence 'client-shutdown.log')
    $client = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $evidence 'client.log')
    if ($shutdown -notmatch 'save_complete saved=true' -or $shutdown -notmatch 'disconnect_complete serverShutdown=true integratedServerPresent=false' -or
        $shutdown -notmatch 'normal_stop_requested' -or $shutdown -match 'prepare_failed|disconnect_incomplete|prepare_refused' -or
        $client -notmatch 'All chunks are saved' -or $client -notmatch 'Stopping' -or
        $client -notmatch 'AE2LF background agent: GLFW creation forced invisible; show/focus disabled') {
        throw 'Missing complete native save, disconnect, hidden-agent or process shutdown evidence'
    }
    $summary['hiddenWindow'] = $hidden
    $summary['gameplayPassed'] = $true
    $summary['normalShutdown'] = $true
    $summary['status'] = 'passed'
}

Assert-WorkspacePath $runMods
if (-not $evidence.StartsWith($resolvedEvidenceRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Evidence must remain within its selected root' }
if ($ValidateExisting) {
    if ($PrepareOnly -or -not $ValidationName) { throw 'ValidateExisting requires a unique ValidationName and cannot prepare/launch a client' }
    if (-not (Test-Path -LiteralPath $evidence -PathType Container)) { throw 'Existing evidence directory not found' }
    $validation = Join-Path $resolvedEvidenceRoot "$EvidenceName-validation-$ValidationName"
    if (Test-Path -LiteralPath $validation) { throw 'Refusing to overwrite validation evidence' }
    function Get-EvidenceSnapshot {
        Get-ChildItem -LiteralPath $evidence -File -Recurse | Sort-Object FullName | ForEach-Object {
            [ordered]@{name=$_.FullName.Substring($evidence.Length + 1);sha256=(Get-Sha256 $_.FullName)}
        }
    }
    $beforeEvidence = @(Get-EvidenceSnapshot)
    $artifact = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $evidence 'artifact.json') | ConvertFrom-Json
    $original = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $evidence 'background-summary.json') | ConvertFrom-Json
    if ($original.exitCode -ne 0 -or $original.runModsRestored -ne $true -or $original.generation -ne $Generation -or $original.chain -ne $Chain) {
        throw 'Original run did not finish normally and restore its mods for this generation/chain'
    }
    if ((Get-Sha256 $production) -ne $artifact.productionSha256 -or (Get-Sha256 $helper) -ne $artifact.helperSha256) { throw 'Original tested artifacts differ from current artifacts' }
    $compatibility = Assert-HelperMatchesProduction
    $identity = Assert-RuntimeMatchesProduction
    $initialMods = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $evidence 'run-mods-before.json') | ConvertFrom-Json
    $restoredMods = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $evidence 'run-mods-after.json') | ConvertFrom-Json
    $actualMods = @(Get-ModSnapshot)
    foreach ($candidate in @($restoredMods, $actualMods)) {
        if ($candidate.Count -ne $initialMods.Count) { throw 'Restored/current mod inventory count differs' }
        foreach ($entry in $initialMods) {
            $matches = @($candidate | Where-Object { $_.name -eq $entry.name -and $_.bytes -eq $entry.bytes -and $_.sha256 -eq $entry.sha256 })
            if ($matches.Count -ne 1) { throw "Restored/current mod inventory differs: $($entry.name)" }
        }
    }
    $summary = [ordered]@{generation=$Generation;chain=$Chain;status='running';sourceEvidence=$evidence;originalStatus=$original.status;
        productionSha256=$artifact.productionSha256;helperSha256=$artifact.helperSha256;helperCompatibility=$compatibility;runtimeIdentity=$identity;
        clientStarted=$false;runModsChanged=$false;runModsRestored=$true;exitCode=0}
    Assert-GameplayEvidence $original.world
    $afterEvidence = @(Get-EvidenceSnapshot)
    if (($beforeEvidence | ConvertTo-Json -Depth 5 -Compress) -ne ($afterEvidence | ConvertTo-Json -Depth 5 -Compress)) { throw 'Read-only validation changed original evidence' }
    $summary['originalEvidenceChanged'] = $false
    New-Item -ItemType Directory -Path $validation | Out-Null
    foreach ($item in @(@{name='summary.json';value=$summary},@{name='evidence-before.json';value=$beforeEvidence},@{name='evidence-after.json';value=$afterEvidence})) {
        [IO.File]::WriteAllText((Join-Path $validation $item.name),(ConvertTo-Json -InputObject $item.value -Depth 20),(New-Object Text.UTF8Encoding($false)))
    }
    Write-Output ($summary | ConvertTo-Json -Depth 8)
    return
}
if ($ValidationName) { throw 'ValidationName is only valid with ValidateExisting' }
if (Test-Path -LiteralPath $evidence) { throw "Refusing to overwrite evidence: $evidence" }
foreach ($required in @($helper,$agent,$production)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Build the isolated artifacts before launching: $required" }
}
$helperCompatibility = Assert-HelperMatchesProduction
$runtimeIdentity = Assert-RuntimeMatchesProduction
if ($PrepareOnly) {
    [ordered]@{generation=$Generation;chain=$Chain;prepared=$true;productionSha256=(Get-Sha256 $production);helperSha256=(Get-Sha256 $helper);helperCompatibility=$helperCompatibility;runtimeIdentity=$runtimeIdentity;clientStarted=$false;runModsChanged=$false} | ConvertTo-Json -Depth 5 | Write-Output
    return
}
$modsExisted = Test-Path -LiteralPath $runMods
New-Item -ItemType Directory -Force -Path $runMods | Out-Null
New-Item -ItemType Directory -Path $evidence | Out-Null
$backup = Join-Path $evidence 'run-mods-backup'
New-Item -ItemType Directory -Path $backup | Out-Null
$lockPath = Join-Path $runRoot '.ae2lo-ripper-background.lock'
$lock = $null
$oldOptions = $env:JAVA_TOOL_OPTIONS
$beforeMods = @()
$removedHelpers = New-Object System.Collections.Generic.List[object]
$staged = Join-Path $runMods ([IO.Path]::GetFileName($helper))
$stagedByThisRun = $false
$summary = [ordered]@{generation=$Generation;chain=$Chain;status='running';evidence=$evidence;startedAt=[DateTime]::UtcNow.ToString('o')}
$originalFailure = $null
try {
    $lock = [IO.File]::Open($lockPath, [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
    $beforeMods = @(Get-ModSnapshot)
    Write-EvidenceJson 'run-mods-before.json' $beforeMods
    $productionHash = Get-Sha256 $production
    $helperHash = Get-Sha256 $helper
    $world = 'AE2LO-Ripper-Probe-' + $Generation + '-' + [Guid]::NewGuid().ToString()
    $artifact = [ordered]@{generation=$Generation;version=$modVersion;productionPath=$production;productionSha256=$productionHash;helperPath=$helper;helperSha256=$helperHash;helperCompatibility=$helperCompatibility;agentPath=$agent;agentSha256=(Get-Sha256 $agent);runtimeIdentity=$runtimeIdentity}
    Write-EvidenceJson 'artifact.json' $artifact
    $summary['world'] = $world
    $summary['productionSha256'] = $productionHash
    $summary['helperSha256'] = $helperHash

    # Only conflicting test helpers are staged out. Every existing mod and .bak stays in its own instance.
    $conflicts = @(Get-ChildItem -LiteralPath $runMods -File | Where-Object {
        $_.Name -match '^ae2l[fo]-runtime-probe-.*\.jar$'
    })
    foreach ($conflict in $conflicts) {
        $saved = Join-Path $backup $conflict.Name
        Copy-Item -LiteralPath $conflict.FullName -Destination $saved
        if ((Get-Sha256 $saved) -ne (Get-Sha256 $conflict.FullName)) { throw "Helper backup differs: $($conflict.Name)" }
        Assert-WorkspacePath $conflict.FullName
        Remove-Item -LiteralPath $conflict.FullName
        $removedHelpers.Add([ordered]@{path=$conflict.FullName;backup=$saved;sha256=(Get-Sha256 $saved)})
    }
    $stagedByThisRun = $true
    Copy-Item -LiteralPath $helper -Destination $staged
    if ((Get-Sha256 $staged) -ne $helperHash) { throw 'Staged ripper helper hash differs' }
    Write-EvidenceJson 'run-mods-active.json' @(Get-ModSnapshot)
    $env:JAVA_TOOL_OPTIONS = '"-javaagent:' + $agent.Replace('\','/') + '" -Dae2lf.probe=false -Dae2lo.probe=true -Dae2lo.probe.background=true -Dae2lo.probe.autoExit=true' +
        ' -Dae2lo.probe.chain=' + $Chain + ' -Dae2lo.probe.world=' + $world +
        ' "-Dae2lo.probe.reportDir=' + $evidence.Replace('\','/') + '"'
    Write-EvidenceJson 'launch.json' ([ordered]@{generation=$Generation;chain=$Chain;world=$world;javaToolOptions=$env:JAVA_TOOL_OPTIONS;launcher=(Join-Path $taskRoot "build-$Generation.ps1");arguments=@('--offline','--no-daemon','runClient');usesPcl=$false})
    Write-EvidenceJson 'background-summary.json' $summary
    Write-Output "$Generation ${Chain}: launching a hidden native client in $world"
    # Execute inside the caller's existing console; do not create a visible PowerShell window.
    # JAVA_TOOL_OPTIONS is informational stderr in Windows PowerShell. Inspect native exit and reports.
    try {
        $ErrorActionPreference = 'Continue'
        & powershell -NoProfile -File (Join-Path $taskRoot "build-$Generation.ps1") --offline --no-daemon runClient *> (Join-Path $evidence 'client.log')
        $nativeExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = 'Stop' }
    $summary['exitCode'] = $nativeExit
    if ($nativeExit -ne 0) { throw "Native client/Gradle exited with code $nativeExit" }
    if ((Get-Sha256 $production) -ne $productionHash) { throw 'Production JAR changed during the run' }
    if ((Get-Sha256 $helper) -ne $helperHash -or (Get-Sha256 $staged) -ne $helperHash) { throw 'Ripper helper changed during the run' }
    $summary['runtimeIdentityAfter'] = Assert-RuntimeMatchesProduction

    Assert-GameplayEvidence $world
} catch {
    $originalFailure = $_
    $summary['status'] = 'failed'
    $summary['failure'] = $_.Exception.Message
} finally {
    $env:JAVA_TOOL_OPTIONS = $oldOptions
    try {
        if ($stagedByThisRun -and (Test-Path -LiteralPath $staged)) {
            Assert-WorkspacePath $staged
            Remove-Item -LiteralPath $staged
        }
        foreach ($savedHelper in $removedHelpers) {
            if (Test-Path -LiteralPath $savedHelper.path) { throw "Refusing to overwrite a concurrently restored helper: $($savedHelper.path)" }
            Copy-Item -LiteralPath $savedHelper.backup -Destination $savedHelper.path
            if ((Get-Sha256 $savedHelper.path) -ne $savedHelper.sha256) { throw 'Restored helper hash differs' }
        }
        if ($null -ne $lock) {
            $afterMods = @(Get-ModSnapshot)
            Write-EvidenceJson 'run-mods-after.json' $afterMods
            if (($beforeMods | ConvertTo-Json -Depth 4 -Compress) -ne ($afterMods | ConvertTo-Json -Depth 4 -Compress)) { throw 'run/mods did not return to its exact original file/hash inventory' }
            $summary['runModsRestored'] = $true
        }
        if (-not $modsExisted -and (Test-Path -LiteralPath $runMods)) {
            Assert-WorkspacePath $runMods
            [IO.Directory]::Delete($runMods, $false)
        }
    } catch {
        $summary['status'] = 'failed'
        $summary['restoreFailure'] = $_.Exception.Message
        if ($null -eq $originalFailure) { $originalFailure = $_ }
    } finally {
        if ($null -ne $lock) { $lock.Dispose() }
        $summary['finishedAt'] = [DateTime]::UtcNow.ToString('o')
        Write-EvidenceJson 'background-summary.json' $summary
        Write-Output ($summary | ConvertTo-Json -Depth 8)
    }
}
if ($null -ne $originalFailure) { throw $originalFailure }
