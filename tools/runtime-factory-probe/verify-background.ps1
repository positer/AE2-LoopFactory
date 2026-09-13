param(
    [Parameter(Mandatory=$true)][ValidateSet('1.21.1','26.1.2')][string]$Generation,
    [Parameter(Mandatory=$true)][ValidatePattern('^[A-Za-z0-9_.-]+$')][string]$EvidenceName,
    [switch]$ValidateExisting,
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9_.-]*$')][string]$ValidationName,
    [switch]$VisualOnly,
    [switch]$PanelVisualOnly,
    [switch]$GroupOnly,
    [switch]$GuideOnly,
    [switch]$UiOnly,
    [switch]$MekOnly,
    [switch]$NativeOnly,
    [switch]$ParserOnly,
    [switch]$SfmCpu,
    [switch]$SelectorCpu,
    [switch]$TagHas,
    [switch]$AddonCompat,
    [switch]$Channel,
    [switch]$ChannelOnly,
    [switch]$RecipeSet,
    [switch]$RecipeSetOnly,
    [switch]$DispatchOnly,
    [switch]$HugeQuantities,
    [switch]$HugeOnly,
    [switch]$RecoveryOnly,
    [switch]$ExtremeOnly,
    [switch]$ChunkLifecycle,
    [switch]$ChunkOnly,
    [switch]$IdInsertionOnly,
    [ValidateSet('none','jei','emi')][string]$IdInsertionViewer='none',
    [ValidateRange(512,16384)][int]$StressCycles=512,
    [ValidateRange(1,32)][int]$BlockingRounds=12,
    [ValidateRange(1,32)][int]$ParallelRounds=12,
    [ValidateRange(12,128)][int]$ParallelBatches=12,
    [ValidateRange(96,1536)][int]$MekBatches=96,
    [ValidateRange(20,100)][int]$TickRate=20
)
$ErrorActionPreference='Stop'
$exclusiveModes=@($VisualOnly,$PanelVisualOnly,$GroupOnly,$GuideOnly,$UiOnly,$MekOnly,$NativeOnly,$ParserOnly,$AddonCompat,$ChannelOnly,$RecipeSetOnly,$DispatchOnly,$HugeOnly,$RecoveryOnly,$ExtremeOnly,$ChunkOnly,$IdInsertionOnly) | Where-Object { $_ }
if(@($exclusiveModes).Count -gt 1){throw 'Choose only one exclusive audit mode; combined filters can silently remove required steps'}
if($ChunkLifecycle -and @($exclusiveModes).Count -gt 0 -and -not $ChunkOnly){throw 'ChunkLifecycle augments the full audit; use ChunkOnly for the independent chunk lifecycle scene'}
if(($MekOnly -or $AddonCompat) -and $Generation -ne '1.21.1'){throw 'MEK and addon fixtures are isolated to 1.21.1'}
if($IdInsertionViewer -ne 'none' -and -not $IdInsertionOnly){throw 'Viewer selection is only valid for the ID insertion audit'}
if($IdInsertionViewer -eq 'emi' -and $Generation -ne '1.21.1'){throw 'No verified native EMI binary is available for 26.1.2'}
if($NativeOnly -and $Generation -ne '26.1.2'){throw 'Modern native/channel-only fixtures are isolated to 26.1.2'}
$taskRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$reportPath=[IO.Path]::GetFullPath((Join-Path $taskRoot "archive/2026-09-08-background-full-audit/$EvidenceName"))
$requestedScale=[ordered]@{stressCycles=$StressCycles;blockingRounds=$BlockingRounds;parallelRounds=$ParallelRounds;parallelBatches=$ParallelBatches;mekBatches=$MekBatches;tickRate=$TickRate}
$runMods=Join-Path $taskRoot "versions/neoforge-$Generation/run/mods"
$helperSource=Join-Path $PSScriptRoot "$Generation/build/libs/ae2lf-runtime-probe-mc$Generation-1.jar"
$productionJar=Join-Path $taskRoot "versions/neoforge-$Generation/build/libs/ae2lf-neoforge-mc$Generation-0.0.5.jar"
if($ValidateExisting -and -not $ValidationName){throw 'ValidateExisting requires a safe unique ValidationName'}
if(-not $ValidateExisting -and $ValidationName){throw 'ValidationName is valid only with ValidateExisting'}
function Assert-RuntimeIdentity([string]$Stage,[string]$OutputDirectory=$reportPath) {
    & python (Join-Path $PSScriptRoot 'verify-runtime-identity.py') --generation $Generation --production $productionJar --report (Join-Path $OutputDirectory "runtime-identity-$Stage.json")
    if($LASTEXITCODE -ne 0){throw "ModDev main classes/resources do not match the production JAR ($Stage); see runtime-identity-$Stage.json"}
}
function Assert-BulkReport([string]$Name,[int]$Batches,[int]$OutputPerBatch){
    $bulk=Get-Content -Raw -LiteralPath (Join-Path $reportPath $Name) | ConvertFrom-Json
    if($bulk.status -ne 'passed' -or @($bulk.orders).Count -ne 3 -or $bulk.targetBatchesPerOrder -ne $Batches -or $bulk.configuredTickRate -ne $TickRate -or $bulk.peakJobs -ne 16){throw "Bulk target/concurrency mismatch: $Name"}
    if(@($bulk.orders | Where-Object {$_.batches -ne $Batches -or $_.admitted -ne $Batches -or $_.completed -ne $Batches -or $_.active -ne 0 -or $_.output -ne $Batches*$OutputPerBatch}).Count -gt 0){throw "Bulk per-order conservation or completion mismatch: $Name"}
}
function Write-ValidationJson([string]$Name,$Value) {
    [IO.File]::WriteAllText((Join-Path $validationPath $Name),(ConvertTo-Json -InputObject $Value -Depth 30),(New-Object Text.UTF8Encoding($false)))
}
function Get-EvidenceManifest {
    foreach($file in @(Get-ChildItem -LiteralPath $reportPath -File -Recurse -Force | Sort-Object FullName)) {
        [ordered]@{name=$file.FullName.Substring($reportPath.Length+1);bytes=$file.Length;sha256=(Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()}
    }
}
function Assert-PreviewPixels {
    $pixelInput=$reportPath
    if($ValidateExisting) {
        # Run the unchanged pixel validator against copies, preserving any existing derived sidecar.
        $pixelInput=Join-Path $validationPath 'preview-recheck'
        New-Item -ItemType Directory -Path (Join-Path $pixelInput 'screenshots') | Out-Null
        Copy-Item -LiteralPath (Join-Path $reportPath 'pattern-preview-report.json') -Destination $pixelInput
        foreach($mode in @('normal','shift','cleared')) {
            Copy-Item -LiteralPath (Join-Path $reportPath "screenshots/factory-pattern-preview-$mode.png") -Destination (Join-Path $pixelInput 'screenshots')
        }
    }
    & python (Join-Path $PSScriptRoot 'verify-preview-pixels.py') $pixelInput
    if($LASTEXITCODE -ne 0){throw 'Native framebuffer target-preview comparison failed'}
    if($ValidateExisting) {
        $generated=Join-Path $pixelInput 'pattern-preview-pixels.json'
        $existing=Join-Path $reportPath 'pattern-preview-pixels.json'
        if(Test-Path -LiteralPath $existing) {
            if((Get-FileHash -LiteralPath $existing -Algorithm SHA256).Hash -ne (Get-FileHash -LiteralPath $generated -Algorithm SHA256).Hash){throw 'Existing preview pixel sidecar differs from independent recomputation; original was preserved'}
        } else {
            # The sole permitted new file in original evidence is this previously absent derived report.
            [IO.File]::Copy($generated,$existing,$false)
        }
    }
}
function Invoke-ExistingValidation {
    $validationError=$null
    $beforeFiles=@()
    $summary=[ordered]@{status='failed';generation=$Generation;evidenceName=$EvidenceName;validationName=$ValidationName;validationDirectory=$validationPath;productionSha256=$null;helperSha256=$null;completedScenes=0;originalFilesUnchanged=$false;nativeExitCode=$null;startedAt=[DateTimeOffset]::Now.ToString('o');mode='read-only existing native evidence; no build, client launch, or run/mods mutation'}
    try {
        if(-not (Test-Path -LiteralPath $reportPath -PathType Container)){throw 'Existing evidence directory is required'}
        $beforeFiles=@(Get-EvidenceManifest)
        if($beforeFiles.Count -eq 0){throw 'Existing evidence is empty'}
        Write-ValidationJson 'original-manifest-before.json' $beforeFiles
        $originalScale=Get-Content -Raw -LiteralPath (Join-Path $reportPath 'requested-scale.json') | ConvertFrom-Json
        if(@($originalScale.PSObject.Properties).Count -ne $requestedScale.Count -or @(Compare-Object @($originalScale.PSObject.Properties.Name) @($requestedScale.Keys)).Count -ne 0){throw 'Original requested-scale keys differ from the supplied scale'}
        foreach($entry in $requestedScale.GetEnumerator()) {
            if([string]$originalScale.($entry.Key) -cne [string]$entry.Value){throw "Original requested-scale differs from supplied value: $($entry.Key)"}
        }
        $originalAudit=Get-Content -Raw -LiteralPath (Join-Path $reportPath 'ui-item-block-audit.json') | ConvertFrom-Json
        $summary.completedScenes=@($originalAudit.results).Count
        $modeValues=[ordered]@{
            visualOnly=[bool]$VisualOnly;panelVisualOnly=[bool]$PanelVisualOnly;groupOnly=[bool]$GroupOnly;guideOnly=[bool]$GuideOnly;uiOnly=[bool]$UiOnly
            mekOnly=[bool]$MekOnly;nativeOnly=[bool]$NativeOnly;parserOnly=[bool]$ParserOnly;sfmCpu=[bool]$SfmCpu;selectorCpu=[bool]$SelectorCpu;tagHas=[bool]$TagHas
            addonCompat=[bool]$AddonCompat;channel=([bool]$Channel -or [bool]$ChannelOnly);channelOnly=[bool]$ChannelOnly
            recipeSet=([bool]$RecipeSet -or [bool]$RecipeSetOnly);recipeSetOnly=[bool]$RecipeSetOnly;dispatchOnly=[bool]$DispatchOnly
            hugeQuantities=([bool]$HugeQuantities -or [bool]$HugeOnly -or [bool]$ExtremeOnly);hugeOnly=[bool]$HugeOnly
            recovery=([bool]$HugeQuantities -or [bool]$RecoveryOnly -or [bool]$ExtremeOnly);recoveryOnly=[bool]$RecoveryOnly;extremeOnly=[bool]$ExtremeOnly
            chunkLifecycle=([bool]$ChunkLifecycle -or [bool]$ChunkOnly);chunkOnly=[bool]$ChunkOnly;idInsertionOnly=[bool]$IdInsertionOnly
        }
        foreach($entry in $modeValues.GetEnumerator()) {
            if($originalAudit.probeProperties.('ae2lf.probe.'+$entry.Key) -cne ([string]$entry.Value).ToLowerInvariant()){throw "Original runtime mode differs from supplied flags: $($entry.Key)"}
        }
        if($originalAudit.probeProperties.'ae2lf.probe.viewerMode' -cne $IdInsertionViewer){throw 'Original runtime viewer mode differs'}
        foreach($requiredTrue in @('background','fullAudit','blocking')){if($originalAudit.probeProperties.('ae2lf.probe.'+$requiredTrue) -cne 'true'){throw "Missing original runtime property: $requiredTrue"}}
        if([IO.Path]::GetFullPath($originalAudit.probeProperties.'ae2lf.probe.reportDir') -ne [IO.Path]::GetFullPath($reportPath)){throw 'Runtime report directory differs from the selected original evidence'}
        $nativeExit=Get-Content -Raw -LiteralPath (Join-Path $reportPath 'process-exit.json') | ConvertFrom-Json
        $summary.nativeExitCode=$nativeExit.exitCode
        if($null -eq $nativeExit.exitCode -or $nativeExit.exitCode -ne 0 -or -not $nativeExit.finishedAt){throw 'Original native process did not finish with exit code zero'}
        if(Test-Path -LiteralPath (Join-Path $reportPath 'shutdown-timeout.txt')){throw 'Original native shutdown timed out'}
        if((Get-Content -Raw -LiteralPath (Join-Path $reportPath 'normal-shutdown.txt')).Trim() -cne 'Integrated server fully stopped; requesting normal process exit'){throw 'Original normal shutdown marker is absent or invalid'}
        $summary.productionSha256=(Get-FileHash -LiteralPath $productionJar -Algorithm SHA256).Hash.ToLowerInvariant()
        $summary.helperSha256=(Get-FileHash -LiteralPath $helperSource -Algorithm SHA256).Hash.ToLowerInvariant()
        $artifact=Get-Content -Raw -LiteralPath (Join-Path $reportPath 'artifact.json') | ConvertFrom-Json
        if($artifact.generation -cne $Generation -or $artifact.productionSha256 -cne $summary.productionSha256 -or $artifact.helperSha256 -cne $summary.helperSha256){throw 'Original artifact does not match the current production and helper JAR hashes'}
        Assert-RuntimeIdentity 'current' $validationPath
        $currentIdentity=Get-Content -Raw -LiteralPath (Join-Path $validationPath 'runtime-identity-current.json') | ConvertFrom-Json
        foreach($stage in @('before','after')) {
            $identity=Get-Content -Raw -LiteralPath (Join-Path $reportPath "runtime-identity-$stage.json") | ConvertFrom-Json
            if($identity.status -cne 'passed' -or $identity.generation -cne $Generation -or $identity.productionSha256 -cne $summary.productionSha256 -or $identity.failure -or @($identity.failures).Count -ne 0){throw "Original runtime identity failed or differs: $stage"}
            if([IO.Path]::GetFullPath($identity.production) -ne [IO.Path]::GetFullPath($productionJar)){throw "Original runtime identity names a different JAR: $stage"}
            foreach($count in @('classCount','resourceCount','runtimeClassCount','runtimeResourceCount')){if($identity.$count -ne $currentIdentity.$count){throw "Original/current runtime inventory count differs: $stage/$count"}}
            if((ConvertTo-Json -InputObject $identity.entries -Depth 8 -Compress) -cne (ConvertTo-Json -InputObject $currentIdentity.entries -Depth 8 -Compress)){throw "Original/current complete class/resource entry hashes differ: $stage"}
        }
        Write-ValidationJson 'validated-request.json' ([ordered]@{requestedScale=$requestedScale;modeFlags=$modeValues;viewerMode=$IdInsertionViewer;originalArtifact=$artifact})
        # Identical full post-run gate shared with normal launches; no per-mode assertions are duplicated or skipped.
        Assert-GameplayEvidence
        $summary.status='passed'
    } catch {
        $validationError=$_
        $summary.failure=$_.Exception.Message
        $summary.failureDetail=$_.ScriptStackTrace
    } finally {
        try {
            $afterFiles=@(Get-EvidenceManifest)
            Write-ValidationJson 'original-manifest-after.json' $afterFiles
            $afterByName=@{}
            foreach($entry in $afterFiles){$afterByName[$entry.name]=$entry}
            $beforeNames=@{}
            $differences=New-Object System.Collections.Generic.List[string]
            foreach($entry in $beforeFiles) {
                $beforeNames[$entry.name]=$true
                if(-not $afterByName.ContainsKey($entry.name) -or $afterByName[$entry.name].bytes -ne $entry.bytes -or $afterByName[$entry.name].sha256 -cne $entry.sha256){$differences.Add("Original file changed or disappeared: $($entry.name)")}
            }
            $newSidecars=New-Object System.Collections.Generic.List[string]
            foreach($entry in $afterFiles) {
                if($beforeNames.ContainsKey($entry.name)){continue}
                $derived=Join-Path $validationPath 'preview-recheck/pattern-preview-pixels.json'
                if($entry.name -ceq 'pattern-preview-pixels.json' -and (Test-Path -LiteralPath $derived) -and $entry.sha256 -ceq (Get-FileHash -LiteralPath $derived -Algorithm SHA256).Hash.ToLowerInvariant()){$newSidecars.Add($entry.name)}
                else{$differences.Add("Unexpected new original evidence file: $($entry.name)")}
            }
            if($summary.productionSha256 -and (Get-FileHash -LiteralPath $productionJar -Algorithm SHA256).Hash.ToLowerInvariant() -cne $summary.productionSha256){$differences.Add('Production JAR changed during validation')}
            if($summary.helperSha256 -and (Get-FileHash -LiteralPath $helperSource -Algorithm SHA256).Hash.ToLowerInvariant() -cne $summary.helperSha256){$differences.Add('Helper JAR changed during validation')}
            Write-ValidationJson 'original-integrity.json' ([ordered]@{beforeFiles=$beforeFiles.Count;afterFiles=$afterFiles.Count;allowedNewDerivedSidecars=@($newSidecars);differences=@($differences)})
            $summary.originalFilesUnchanged=($beforeFiles.Count -gt 0 -and $differences.Count -eq 0)
            $summary.allowedNewDerivedSidecars=@($newSidecars)
            if(-not $summary.originalFilesUnchanged){throw "Original evidence integrity failed: $($differences -join '; ')"}
        } catch {
            $summary.status='failed'
            $summary.integrityFailure=$_.Exception.Message
            if($null -eq $validationError){$validationError=$_}
        }
        $summary.finishedAt=[DateTimeOffset]::Now.ToString('o')
        Write-ValidationJson 'validation-summary.json' $summary
        Write-Output (ConvertTo-Json -InputObject $summary -Depth 10)
    }
    if($null -ne $validationError){throw $validationError}
}

function Assert-GameplayEvidence {
    $audit=Get-Content -Raw -LiteralPath (Join-Path $reportPath 'ui-item-block-audit.json') | ConvertFrom-Json
    Write-Output "$Generation background audit: $($audit.status); $($audit.results.Count) rows"
    $audit.results | Where-Object { -not $_.passed } | Select-Object id,failure,serverError
    $planned=@($audit.plannedSteps)
    $observed=@($audit.results | ForEach-Object id)
    if($planned.Count -eq 0 -or @($planned | Select-Object -Unique).Count -ne $planned.Count -or $observed.Count -ne $planned.Count -or @($observed | Select-Object -Unique).Count -ne $observed.Count -or @(Compare-Object $planned $observed).Count -ne 0){throw 'Planned gameplay scenes were not each executed exactly once; completed alone is insufficient'}
    foreach($entry in $requestedScale.GetEnumerator()){if($audit.probeProperties.('ae2lf.probe.'+$entry.Key) -ne [string]$entry.Value){throw "Runtime scale differs from request: $($entry.Key); helper or launch parameters are stale"}}
    if($audit.status -ne 'completed' -or @($audit.results).Count -eq 0 -or @($audit.results | Where-Object { -not $_.passed -or $_.windowVisible -ne 0 }).Count -gt 0) { throw 'Background audit did not pass every step invisibly' }
    if(Test-Path -LiteralPath (Join-Path $reportPath 'shutdown-timeout.txt')){throw 'Shutdown timed out; bounded or forced exit cannot pass normal shutdown'}
    if(-not (Test-Path -LiteralPath (Join-Path $reportPath 'normal-shutdown.txt'))){throw 'Missing normal shutdown evidence'}
    if($IdInsertionOnly){
        if($planned.Count -ne 1 -or $planned[0] -ne 'code-id-insertion'){throw 'ID insertion audit did not execute exactly its required native scene'}
        $insertion=Get-Content -Raw -LiteralPath (Join-Path $reportPath 'item-id-insertion-report.json') | ConvertFrom-Json
        if($insertion.status -ne 'passed' -or $insertion.viewerMode -ne $IdInsertionViewer){throw 'Native code ID insertion report failed or viewer differs'}
        if($insertion.loadedJei -ne ($IdInsertionViewer -eq 'jei') -or $insertion.loadedEmi -ne ($IdInsertionViewer -eq 'emi')){throw 'ID insertion viewer mod isolation differs from the requested profile'}
        & python (Join-Path $PSScriptRoot 'verify-id-insertion.py') $reportPath --generation $Generation --viewer $IdInsertionViewer
        if($LASTEXITCODE -ne 0){throw 'Native ID insertion per-case text, caret, resource, save, or framebuffer verification failed'}
    }
    if($GroupOnly) {
        $group=Get-Content -Raw -LiteralPath (Join-Path $reportPath 'multi-tag-report.json') | ConvertFrom-Json
        if($group.status -ne 'passed') {throw 'Group fixture failed'}
    }
    if($MekOnly){if((Get-Content -Raw (Join-Path $reportPath "mekanism-bulk-report.json") | ConvertFrom-Json).status -ne "passed"){throw "MEK bulk fixture failed"};if((Get-Content -Raw (Join-Path $reportPath "native-capability-report.json") | ConvertFrom-Json).status -ne "passed"){throw "Native chemistry/FE fixture failed"}}
    if($NativeOnly){foreach($required in @('native-transactions-report.json','parallel-orders-report.json','compatibility-audit.json')){if((Get-Content -Raw (Join-Path $reportPath $required) | ConvertFrom-Json).status -ne 'passed'){throw "Modern native report failed: $required"}}}
    if($AddonCompat){
        foreach($required in @('compatibility-audit.json','addon-soul-report.json')){
            if((Get-Content -Raw -LiteralPath (Join-Path $reportPath $required) | ConvertFrom-Json).status -ne 'passed'){throw "Addon compatibility fixture failed: $required"}
        }
    }
    if($Channel -or $ChannelOnly){
        $channelReport=Get-Content -Raw -LiteralPath (Join-Path $reportPath 'channel-report.json') | ConvertFrom-Json
        if($channelReport.status -ne 'passed'){throw 'Indentation channel fixture failed'}
        if($channelReport.complete_highlighting_native_passed -ne $true -or (Get-Content -Raw (Join-Path $reportPath 'syntax-highlight-report.json') | ConvertFrom-Json).status -ne 'passed'){throw 'Native complete syntax highlighting failed'}
        if($channelReport.function_channel_isolation_passed -ne $true -or (Get-Content -Raw (Join-Path $reportPath 'channel-function-report.json') | ConvertFrom-Json).status -ne 'passed'){throw 'Channel function isolation failed'}
        foreach($assertion in @(
            'native_channel_editor_captured','exact_initial_inventory','exact_channel_source_and_destination_balances',
            'channel_1_trillion_must_does_not_block_channel_0_empty_put',
            'channel_0_empty_put_leaves_physical_stock_and_channel_1_quota_unchanged',
            'channel_1_trillion_must_does_not_block_channel_0_real_transfer',
            'channel_0_moves_exactly_three_without_borrowing_channel_1_quota',
            'same_channel_must_waits_for_exact_missing_two',
            'same_channel_native_codec_preserves_pending_transfer_without_replay',
            'legacy_save_fixture_changes_only_transfer_remaining',
            'legacy_inflated_debt_is_reproduced_without_mutating_physical_stock',
            'legacy_inflated_debt_normalizes_to_own_channel_exact_remaining_two',
            'same_channel_must_remains_blocked_without_stock_or_replay',
            'same_channel_refill_adds_exactly_two_physical_items',
            'same_channel_must_finishes_only_after_real_refill',
            'must_regression_exact_conservation_six_initial_plus_two_refill_equals_eight_delivered',
            'must_regression_preserves_original_channel_balances',
            'real_owner_and_original_tags_retained_after_must_regression')) {
            if($channelReport.$assertion -ne $true){throw "Missing or failed native Channel/MUST assertion: $assertion"}
        }
        $must=$channelReport.channelMustRegression
        if($must.status -ne 'passed' -or $must.initialPhysicalIron -ne 6 -or $must.explicitRefillIron -ne 2 -or $must.blockedServerTicksAfterRestore -lt 20){throw 'Native Channel/MUST case count, supply, or real blocked-tick evidence is incomplete'}
        $mustStages=[ordered]@{
            crossChannelWithoutSourceAfter=@(6,0,$true,$false,0,1000000000000)
            crossChannelWithSourceAfter=@(3,3,$true,$false,0,1000000000000)
            sameChannelPartial=@(0,6,$false,$true,2,2)
            sameChannelRestored=@(0,6,$false,$true,2,2)
            sameChannelLegacyStateRestored=@(0,6,$false,$true,1000000000002,2)
            sameChannelBlockedAfterRestore=@(0,6,$false,$true,2,2)
            sameChannelAfterPhysicalRefill=@(2,6,$false,$true,2,2)
            sameChannelCompleted=@(0,8,$true,$false,0,0)
        }
        foreach($name in $mustStages.Keys){
            $row=$must.$name;$expected=$mustStages[$name]
            if($null -eq $row -or $row.sourceIron -ne $expected[0] -or $row.destinationIron -ne $expected[1] -or $row.finished -ne $expected[2] -or $row.error -ne ''){throw "Native Channel/MUST physical stage failed or is missing: $name"}
            $continuation=$row.savedJob.continuation | ConvertFrom-Json
            $routes=@($row.savedJob.routes | ConvertFrom-Json)
            $ownedRoutes=@($routes | Where-Object {$_.channel -eq 1 -and $_.must -eq $true})
            if($continuation.pendingTransfer -ne $expected[3] -or $continuation.transferRemaining -ne $expected[4] -or $ownedRoutes.Count -ne 1 -or $ownedRoutes[0].remaining -ne $expected[5]){throw "Native Channel/MUST actual Saved quota or continuation mismatch: $name"}
        }
        $outerRoutes=@(($must.crossChannelWithSourceAfter.savedJob.routes | ConvertFrom-Json) | Where-Object {$_.channel -eq 0})
        if($outerRoutes.Count -ne 1 -or $outerRoutes[0].remaining -ne 0){throw 'Channel-0 real source quota did not settle exactly'}
    }
    if($RecipeSet -or $RecipeSetOnly){
        if((Get-Content -Raw -LiteralPath (Join-Path $reportPath 'recipe-set-report.json') | ConvertFrom-Json).status -ne 'passed'){throw 'Complete recipe set fixture failed'}
    }
    if($DispatchOnly){
        if(@($audit.results).Count -ne 1 -or $audit.results[0].id -ne 'complex-factory-flows' -or @($audit.plannedSteps).Count -ne 1 -or $audit.plannedSteps[0] -ne 'complex-factory-flows'){throw 'DispatchOnly did not execute exactly the complex-factory-flows step'}
        if((Get-Content -Raw -LiteralPath (Join-Path $reportPath 'complex-flow-report.json') | ConvertFrom-Json).status -ne 'passed'){throw 'Dispatch stability fixture failed'}
    }
    if($HugeQuantities -or $HugeOnly -or $ExtremeOnly){
        $huge=Get-Content -Raw -LiteralPath (Join-Path $reportPath 'huge-quantity-report.json') | ConvertFrom-Json
        if($huge.status -ne 'passed' -or $huge.targetCases -ne 21 -or $huge.completedCases -ne 21 -or @($huge.cases).Count -ne 21 -or $huge.categoryCounts.direct -ne 15 -or $huge.categoryCounts.partialMust -ne 3 -or $huge.categoryCounts.saturatedReturn -ne 3 -or $huge.jobCodecRestores -ne 33 -or $huge.cellBinaryNbtRoundTrips -ne 108 -or $huge.configuredTickRate -ne $TickRate){throw 'Extreme-quantity native AE logistics coverage or persistence count mismatch'}
        if(@($huge.cases | Where-Object {$_.status -ne 'passed' -or $_.conserved -ne $true -or $_.actualMovedDecimal -ne $_.requestedQuantityDecimal -or ($_.category -eq 'direct' -and $_.jobTicks -ne 1) -or ($_.category -ne 'direct' -and $_.jobTicks -ne 3)}).Count -gt 0){throw 'Extreme-quantity movement, conservation, or bounded tick cost failed'}
        if($HugeOnly -and ($planned.Count -ne 1 -or $planned[0] -ne 'huge-quantity-logistics')){throw 'HugeOnly did not execute its required native scene'}
    }
    if(@($audit.results | Where-Object {$_.id -eq 'complex-factory-flows'}).Count -gt 0){
        $flow=Get-Content -Raw -LiteralPath (Join-Path $reportPath 'complex-flow-report.json') | ConvertFrom-Json
        $expectedCpuOrders=4+$BlockingRounds+2*$ParallelRounds
        $expectedPrimary=2*($BlockingRounds+2*$ParallelRounds)
        if($flow.status -ne 'passed' -or $flow.stabilityBlockingRounds -ne $BlockingRounds -or $flow.stabilityParallelRounds -ne $ParallelRounds -or $flow.submittedCpuOrders -ne $expectedCpuOrders -or $flow.stabilityTotalReturnedPrimary -ne $expectedPrimary -or $flow.configuredTickRate -ne $TickRate){throw 'Dispatch stability actual rounds/orders/returns differ from requested scale'}
        if(@($flow.stabilityRounds | Where-Object {$_.codecRestores -le 0 -or $_.primaryDelta -ne $_.batches -or $_.inputResidue -ne 0 -or $_.duplicateReturns -ne 0 -or $_.lostReturns -ne 0}).Count -gt 0){throw 'A stability round lacks exact return/conservation/continuation evidence'}
    }
    if(@($audit.results | Where-Object {$_.id -eq 'parallel-native-orders'}).Count -gt 0){Assert-BulkReport 'parallel-orders-report.json' $ParallelBatches 1}
    if($MekOnly){Assert-BulkReport 'mekanism-bulk-report.json' $MekBatches 12}
    if($HugeQuantities -or $RecoveryOnly -or $ExtremeOnly){
        $recovery=Get-Content -Raw -LiteralPath (Join-Path $reportPath 'huge-recovery-report.json') | ConvertFrom-Json
        if($recovery.status -ne 'passed' -or $recovery.targetCases -ne 3 -or $recovery.completedCases -ne 3 -or @($recovery.cases).Count -ne 3 -or $recovery.configuredTickRate -ne $TickRate){throw 'Extreme-quantity machine removal and recovery coverage failed'}
        if(@($recovery.cases | Where-Object {$_.status -ne 'passed' -or $_.conserved -ne $true}).Count -gt 0){throw 'A physical recovery case failed its exact conservation check'}
        if($recovery.cases[0].actualReturnedDecimal -ne '18446744073709543422'){throw 'Segmented recovery did not return the full amount above Long.MAX_VALUE'}
        if($recovery.longProgramPreflight.status -ne 'passed' -or $recovery.longProgramPreflight.maxLengthQueuedTasksCancelledWithoutPayload -ne $true){throw 'Maximum-length queued jobs were not safely cancelled without aggregating program payloads'}
        if($RecoveryOnly -and ($planned.Count -ne 1 -or $planned[0] -ne 'huge-recovery-logistics')){throw 'RecoveryOnly did not execute its required native scene'}
    }
    if($ChunkLifecycle -or $ChunkOnly){
        $chunk=Get-Content -Raw -LiteralPath (Join-Path $reportPath 'chunk-lifecycle-report.json') | ConvertFrom-Json
        $chunkCaseNames=@('remote_storage_unload_must','whole_owner_unload_must','partial_return_cargo_unload')
        if($chunk.status -ne 'passed' -or $chunk.targetCases -ne 3 -or $chunk.completedCases -ne 3 -or @($chunk.cases).Count -ne 3 -or @($chunk.cases.case | Select-Object -Unique).Count -ne 3 -or @(Compare-Object @($chunk.cases.case) $chunkCaseNames).Count -ne 0){throw 'Chunk lifecycle case coverage is incomplete or duplicated'}
        if($chunk.manualJobTicks -ne 0 -or $chunk.manuallyInvokedUnloadCallbacks -ne 0 -or $chunk.playerMustRemainBeyondBlocks -ne 512){throw 'Chunk lifecycle used manual execution/lifecycle callbacks or an incorrect player-distance contract'}
        foreach($case in $chunk.cases){
            if($case.status -ne 'passed' -or $case.unloadObserved -ne $true -or $case.reloadedNewBlockEntity -ne $true -or $case.conserved -ne $true -or $case.resumed -ne $true -or $case.unloadedServerTicks -lt 20 -or $case.all_players_remain_over_512_blocks_from_fixture -ne $true){throw "Chunk lifecycle lacks genuine unload, new block entity, continuation, or conservation: $($case.case)"}
            $before=@($case.observations | Where-Object {$_.stage -eq 'loaded_before_unload'})
            $unloaded=@($case.observations | Where-Object {$_.stage -eq 'unloaded'})
            $reloaded=@($case.observations | Where-Object {$_.stage -eq 'reloaded'})
            if($before.Count -eq 0 -or $unloaded.Count -eq 0 -or $reloaded.Count -eq 0){throw "Missing native chunk lifecycle observations: $($case.case)"}
            if(@($case.observations | Where-Object {$null -eq $_.playerDistanceSquared -or $_.playerDistanceSquared -lt 262144}).Count -gt 0){throw 'A player approached a lifecycle fixture and could have kept its chunks loaded'}
            foreach($sample in @($before)+@($reloaded)){
                foreach($side in @('ownerChunk','remoteChunk')){if($sample.$side.hasChunk -ne $true -or $sample.$side.chunkNow -ne $true -or $sample.$side.blockEntityVisible -ne $true){throw "Expected loaded native block entity is absent: $($case.case)/$side/$($sample.stage)"}}
            }
            foreach($sample in $unloaded){
                if($sample.remoteChunk.hasChunk -ne $false -or $sample.remoteChunk.chunkNow -ne $false -or $sample.remoteChunk.blockEntityVisible -ne $false -or $sample.oldRemoteRemoved -ne $true){throw "Remote chunk was not actually unloaded: $($case.case)"}
                if($case.case -eq 'remote_storage_unload_must'){
                    if($sample.ownerChunk.hasChunk -ne $true -or $sample.ownerChunk.chunkNow -ne $true -or $sample.ownerChunk.blockEntityVisible -ne $true -or $sample.oldOwnerRemoved -ne $false){throw 'Remote-only unload did not retain its real owner'}
                }else{
                    if($sample.ownerChunk.hasChunk -ne $false -or $sample.ownerChunk.chunkNow -ne $false -or $sample.ownerChunk.blockEntityVisible -ne $false -or $sample.oldOwnerRemoved -ne $true -or $case.registryOwnerAbsent -ne $true){throw "Whole owner unload left a loaded owner or registry entry: $($case.case)"}
                }
            }
            if($reloaded[0].serverTick - $unloaded[0].serverTick -lt 20){throw "Reload followed less than 20 observed unloaded server ticks: $($case.case)"}
            if($null -eq $before[0].remoteIdentity -or $null -eq $reloaded[0].remoteIdentity -or $before[0].remoteIdentity -eq $reloaded[0].remoteIdentity){throw "Remote storage did not reload as a different native block entity: $($case.case)"}
            if($case.case -eq 'remote_storage_unload_must'){
                if($before[0].ownerIdentity -ne $reloaded[0].ownerIdentity -or $case.initialIron -ne 17 -or $case.explicitRefillIron -ne 12 -or $case.finalDeliveredIron -ne '29' -or $case.remoteGoldWitness -ne 31 -or $case.remoteTaggedDiamondsWitness -ne 7 -or $case.ghostMeReadsBlocked -ne $true -or $case.ghostTaggedReadsBlocked -ne $true){throw 'Remote-only unload physical supply, retained owner, or absent-resource reads failed'}
            }else{
                if($null -eq $before[0].ownerIdentity -or $null -eq $reloaded[0].ownerIdentity -or $before[0].ownerIdentity -eq $reloaded[0].ownerIdentity){throw "Owner did not reload as a different native block entity: $($case.case)"}
                if($case.case -eq 'whole_owner_unload_must'){
                    if($case.previousDeliveredIron -ne 29 -or $case.initialIron -ne 5 -or $case.explicitRefillIron -ne 8 -or $case.finalDeliveredIron -ne '42' -or $case.native_owner_reload_restores_exact_pending_eight_and_owned_state -ne $true){throw 'Whole-owner native reload did not retain the exact pending quota and physical supply'}
                }else{
                    # Decimal is exact for these 19-digit totals and avoids signed-long addition overflow.
                    $expectedTotal=[decimal]$case.previousDeliveredIron+[decimal]$case.explicitPrefillIron+[decimal]$case.explicitInputIron
                    if($case.previousDeliveredIron -ne 42 -or $case.explicitPrefillIron -ne '9223372036854775752' -or $case.explicitInputIron -ne 37 -or $case.conservationTotalDecimal -ne '9223372036854775831' -or [decimal]$case.conservationTotalDecimal -ne $expectedTotal -or $case.beforeUnloadPendingIron -ne 19 -or $case.afterReloadPendingIron -ne 19 -or $case.beforeUnloadReceiptIron -ne 5 -or $case.afterReloadReceiptIron -ne 5 -or $case.finalMainIron -ne '9223372036854775807' -or $case.finalReceiptIron -ne 24 -or $case.finalPendingIron -ne 0 -or [decimal]$case.finalMainIron+[decimal]$case.finalReceiptIron -ne $expectedTotal){throw 'Partly returned cargo did not preserve its exact above-long-limit physical ledger through native unload/reload'}
                }
            }
        }
        if(@($observed | Where-Object {$_ -eq 'chunk-lifecycle-logistics'}).Count -ne 1){throw 'Chunk lifecycle scene was not executed exactly once'}
        if($ChunkOnly -and ($planned.Count -ne 1 -or $planned[0] -ne 'chunk-lifecycle-logistics')){throw 'ChunkOnly did not execute exactly its independent native lifecycle scene'}
    }
    if(@($observed | Where-Object {$_ -like 'factory-pattern-preview-*'}).Count -gt 0){
        $preview=Get-Content -Raw -LiteralPath (Join-Path $reportPath 'pattern-preview-report.json') | ConvertFrom-Json
        if($preview.status -ne 'passed'){throw 'Native factory pattern target previews failed'}
        Assert-PreviewPixels
    }
    if($ExtremeOnly -and ($planned.Count -ne 5 -or @(Compare-Object $planned @('huge-quantity-logistics','huge-recovery-logistics','factory-pattern-preview-normal','factory-pattern-preview-shift','factory-pattern-preview-cleared')).Count -ne 0)){throw 'ExtremeOnly did not run all quantity, recovery, and preview scenes'}
    if(-not $ChunkOnly -and -not $ExtremeOnly -and -not $RecoveryOnly -and -not $HugeOnly -and -not $ParserOnly -and -not $NativeOnly -and -not $MekOnly -and -not $AddonCompat -and -not $ChannelOnly -and -not $RecipeSetOnly -and -not $DispatchOnly -and -not $VisualOnly -and -not $PanelVisualOnly -and -not $UiOnly -and -not $GroupOnly -and -not $GuideOnly -and -not $IdInsertionOnly) {
        foreach($requiredReport in @('factory-report.json','native-cpu-report.json','compatibility-audit.json','topology-report.json','terminal-report.json','induction-report.json','multi-tag-report.json','complex-flow-report.json')) {
            $result=Get-Content -Raw -LiteralPath (Join-Path $reportPath $requiredReport) | ConvertFrom-Json
            if($result.status -ne 'passed') { throw "Failed independent report: $requiredReport" }
        }
        if($Generation -eq '26.1.2'){foreach($nativeReport in @('native-transactions-report.json','parallel-orders-report.json')){if((Get-Content -Raw (Join-Path $reportPath $nativeReport) | ConvertFrom-Json).status -ne 'passed'){throw "Modern native gate failed: $nativeReport"}}}
        $stress=Get-Content -Raw -LiteralPath (Join-Path $reportPath 'stress-report.json') | ConvertFrom-Json
        if($stress.status -ne 'passed' -or $stress.completedRoundTrips -ne 8*$StressCycles -or $stress.jobCodecRestores -ne 16*$StressCycles -or $stress.cyclesPerNetwork -ne $StressCycles -or $stress.configuredTickRate -ne $TickRate) { throw 'Incomplete world stress evidence' }
    }
}
if($ValidateExisting) {
    $validationPath=[IO.Path]::GetFullPath((Join-Path ([IO.Path]::GetDirectoryName($reportPath)) "$EvidenceName-revalidation-$ValidationName"))
    if([IO.Path]::GetDirectoryName($validationPath) -ne [IO.Path]::GetDirectoryName($reportPath)){throw 'Revalidation output must be an exact sibling of original evidence'}
    if(Test-Path -LiteralPath $validationPath){throw 'Refusing to overwrite revalidation evidence; choose a unique ValidationName'}
    New-Item -ItemType Directory -Path $validationPath | Out-Null
    Invoke-ExistingValidation
    return
}
if(Test-Path -LiteralPath $reportPath){throw 'Refusing to overwrite evidence'}
New-Item -ItemType Directory -Path $reportPath | Out-Null
$requestedScale | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $reportPath 'requested-scale.json')
Write-Output "Background audit target: $Generation; $EvidenceName; $(ConvertTo-Json -Compress $requestedScale)"
Copy-Item -LiteralPath (Join-Path $PSScriptRoot "$Generation/build/libs/ae2lf-runtime-probe-mc$Generation-1.jar") -Destination $runMods
$productionHash=(Get-FileHash -LiteralPath $productionJar -Algorithm SHA256).Hash.ToLower()
@{generation=$Generation;productionSha256=$productionHash;helperSha256=(Get-FileHash -LiteralPath (Join-Path $runMods "ae2lf-runtime-probe-mc$Generation-1.jar") -Algorithm SHA256).Hash.ToLower()} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $reportPath 'artifact.json')
Assert-RuntimeIdentity 'before'

$dependencyPath="C:/Users/12252/Desktop/Files/Minecraft/PCL/.minecraft/versions/AE2-lightoptimizer-$Generation/mods"
if(-not $MekOnly){Get-ChildItem -LiteralPath $dependencyPath | Where-Object { $_.Name -like 'AppliedFlux*' -or $_.Name -like 'Glodium*' } | Copy-Item -Destination $runMods}
$agentPath=(Join-Path $PSScriptRoot 'background-agent/hidden-window-agent.jar').Replace('\','/')
if($MekOnly){
    if($Generation -ne '1.21.1'){throw 'MEK fixture is generation-isolated to 1.21.1'}
    Copy-Item -LiteralPath (Join-Path $taskRoot 'archive/2026-09-09-mekanism-bulk/inputs/Mekanism-1.21.1-10.7.19.85.jar') -Destination $runMods
}
if($AddonCompat){
    if($Generation -ne '1.21.1'){throw 'Addon compatibility fixture is generation-isolated to 1.21.1'}
    $addonSource='C:/Users/12252/Desktop/Files/Minecraft/PCL/.minecraft/versions/ImmortalStorage-1.21.1/mods'
    # Botania and Applied Botanics are deliberately absent: appbot 1.6.0-alpha.3 throws
    # "NullPointerException: capability" inside AE2's RegisterPartCapabilitiesEvent in this dev runtime,
    # which aborts the whole mod load. That crash is recorded in archive/.../addon-compat3-1.21.1-20260912.
    foreach($name in @('ars_nouveau-1.21.1-5.12.1.jar','arseng-2.1.1-beta.jar','industrial-foregoing-souls-1.21.1-1.10.7.jar','industrialforegoing-1.21-3.6.39.jar','titanium-1.21-4.0.45.jar','curios-neoforge-9.5.1+1.21.1.jar','geckolib-neoforge-1.21.1-4.9.2.jar')){
        $source=Join-Path $addonSource $name
        if(-not (Test-Path -LiteralPath $source)){throw "Missing addon fixture dependency: $source"}
        Copy-Item -LiteralPath $source -Destination $runMods
    }
    # Patchouli ships inside the neighbouring instance's mod as a nested jar; copy it out for this fixture only.
    $patchouli=Join-Path $PSScriptRoot 'dependencies/Patchouli-1.21.1-93-NEOFORGE.jar'
    if(-not (Test-Path -LiteralPath $patchouli)){throw "Missing addon fixture dependency: $patchouli"}
    Copy-Item -LiteralPath $patchouli -Destination $runMods
}
$previousOptions=$env:JAVA_TOOL_OPTIONS
try {
    $env:JAVA_TOOL_OPTIONS='"-javaagent:'+$agentPath+'" -Dae2lf.probe=true -Dae2lf.probe.blocking=true -Dae2lf.probe.background=true -Dae2lf.probe.fullAudit=true -Dae2lf.probe.visualOnly=false -Dae2lf.probe.panelVisualOnly=false -Dae2lf.probe.groupOnly=false -Dae2lf.probe.guideOnly=false -Dae2lf.probe.uiOnly=false -Dae2lf.probe.mekOnly=false -Dae2lf.probe.nativeOnly=false -Dae2lf.probe.parserOnly=false -Dae2lf.probe.sfmCpu=false -Dae2lf.probe.selectorCpu=false -Dae2lf.probe.tagHas=false -Dae2lf.probe.addonCompat=false -Dae2lf.probe.channel=false -Dae2lf.probe.channelOnly=false -Dae2lf.probe.recipeSet=false -Dae2lf.probe.recipeSetOnly=false -Dae2lf.probe.dispatchOnly=false "-Dae2lf.probe.reportDir='+$reportPath.Replace('\','/')+'"'
    foreach($entry in $requestedScale.GetEnumerator()){$env:JAVA_TOOL_OPTIONS += " -Dae2lf.probe.$($entry.Key)=$($entry.Value)"}
    $env:JAVA_TOOL_OPTIONS += ' -Dae2lf.probe.hugeQuantities=false -Dae2lf.probe.hugeOnly=false'
    if($HugeQuantities -or $HugeOnly -or $ExtremeOnly){$env:JAVA_TOOL_OPTIONS += ' -Dae2lf.probe.hugeQuantities=true'}
    if($HugeOnly){$env:JAVA_TOOL_OPTIONS += ' -Dae2lf.probe.hugeOnly=true'}
    $env:JAVA_TOOL_OPTIONS += ' -Dae2lf.probe.recovery=false -Dae2lf.probe.recoveryOnly=false'
    if($HugeQuantities -or $RecoveryOnly -or $ExtremeOnly){$env:JAVA_TOOL_OPTIONS += ' -Dae2lf.probe.recovery=true'}
    if($RecoveryOnly){$env:JAVA_TOOL_OPTIONS += ' -Dae2lf.probe.recoveryOnly=true'}
    $env:JAVA_TOOL_OPTIONS += ' -Dae2lf.probe.extremeOnly=false'
    if($ExtremeOnly){$env:JAVA_TOOL_OPTIONS += ' -Dae2lf.probe.extremeOnly=true'}
    $env:JAVA_TOOL_OPTIONS += ' -Dae2lf.probe.chunkLifecycle=false -Dae2lf.probe.chunkOnly=false'
    $env:JAVA_TOOL_OPTIONS += ' -Dae2lf.probe.idInsertionOnly=false'
    if($IdInsertionOnly){$env:JAVA_TOOL_OPTIONS += ' -Dae2lf.probe.idInsertionOnly=true'}
    $env:JAVA_TOOL_OPTIONS += " -Dae2lf.probe.viewerMode=$IdInsertionViewer"
    if($ChunkLifecycle -or $ChunkOnly){$env:JAVA_TOOL_OPTIONS += ' -Dae2lf.probe.chunkLifecycle=true'}
    if($ChunkOnly){$env:JAVA_TOOL_OPTIONS += ' -Dae2lf.probe.chunkOnly=true'}
    if($ParserOnly){$env:JAVA_TOOL_OPTIONS += " -Dae2lf.probe.parserOnly=true"}
    if($NativeOnly){if($Generation -ne '26.1.2'){throw 'NativeOnly targets modern transactional interfaces'};$env:JAVA_TOOL_OPTIONS += " -Dae2lf.probe.nativeOnly=true"}
    if($MekOnly){$env:JAVA_TOOL_OPTIONS += " -Dae2lf.probe.mekOnly=true"}
    if($PanelVisualOnly){$env:JAVA_TOOL_OPTIONS += " -Dae2lf.probe.panelVisualOnly=true"}
    if($VisualOnly){$env:JAVA_TOOL_OPTIONS += " -Dae2lf.probe.visualOnly=true"}
    if($GuideOnly){$env:JAVA_TOOL_OPTIONS += " -Dae2lf.probe.guideOnly=true"}
    if($GroupOnly){$env:JAVA_TOOL_OPTIONS += " -Dae2lf.probe.groupOnly=true"}
    if($UiOnly){$env:JAVA_TOOL_OPTIONS += " -Dae2lf.probe.uiOnly=true"}
    if($SfmCpu){$env:JAVA_TOOL_OPTIONS += " -Dae2lf.probe.sfmCpu=true"}
    if($SelectorCpu){$env:JAVA_TOOL_OPTIONS += " -Dae2lf.probe.selectorCpu=true"}
    if($TagHas){$env:JAVA_TOOL_OPTIONS += " -Dae2lf.probe.tagHas=true"}
    if($AddonCompat){$env:JAVA_TOOL_OPTIONS += " -Dae2lf.probe.addonCompat=true"}
    if($Channel){$env:JAVA_TOOL_OPTIONS += " -Dae2lf.probe.channel=true"}
    if($ChannelOnly){$env:JAVA_TOOL_OPTIONS += " -Dae2lf.probe.channel=true -Dae2lf.probe.channelOnly=true"}
    if($RecipeSet -or $RecipeSetOnly){$env:JAVA_TOOL_OPTIONS += " -Dae2lf.probe.recipeSet=true"}
    if($RecipeSetOnly){$env:JAVA_TOOL_OPTIONS += " -Dae2lf.probe.recipeSetOnly=true"}
    if($DispatchOnly){$env:JAVA_TOOL_OPTIONS += " -Dae2lf.probe.dispatchOnly=true"}
    # Windows PowerShell reports JAVA_TOOL_OPTIONS informational stderr as NativeCommandError.
    # Preserve stderr in evidence and judge the actual process exit plus independent reports.
    try {
        $ErrorActionPreference='Continue'
        & powershell -NoProfile -File (Join-Path $taskRoot "build-$Generation.ps1") --offline --no-daemon runClient *> (Join-Path $reportPath 'client.log')
        $clientExit=$LASTEXITCODE
    } finally { $ErrorActionPreference='Stop' }
    @{exitCode=$clientExit;finishedAt=[DateTimeOffset]::Now.ToString('o')} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $reportPath 'process-exit.json')
    if($clientExit -ne 0){throw "Background client failed: $reportPath"}
    if((Get-FileHash -LiteralPath $productionJar -Algorithm SHA256).Hash.ToLower() -ne $productionHash){throw 'Production artifact changed during runtime launch; rebuild before testing'}
    Assert-RuntimeIdentity 'after'
    Assert-GameplayEvidence
} finally {$env:JAVA_TOOL_OPTIONS=$previousOptions}
