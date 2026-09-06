#requires -Version 5.1
<#
.SYNOPSIS
Launch the isolated AE2LO runtime probe in an existing independent PCL instance.
.DESCRIPTION
Reads installed version metadata and libraries without using PCL account data or
cached launch commands. The probe creates a fresh world; existing saves are never
opened or copied. PrepareOnly validates and writes safe offline arguments without
starting Java, and permits a missing probe JAR while it is still being built.
.EXAMPLE
powershell -NoProfile -File .\tools\runtime-ripper-probe\launch-pcl-probe.ps1 -Generation 1.21.1 -EvidenceDir C:\Temp\ae2lo-probe-121 -PrepareOnly
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('1.21.1', '26.1.2')]
    [string] $Generation,
    [Parameter(Mandatory = $true)]
    [string] $EvidenceDir,
    [switch] $PrepareOnly,
    [string] $PclRoot = (Join-Path $env:USERPROFILE 'Desktop/Files/Minecraft/PCL'),
    [string] $JavaHome,
    [ValidateSet('template', 'smithing', 'layered', 'core256m', 'catalog_audit', 'native_catalog', 'exact_components', 'pack_catalog', 'tool_components')]
    [string] $Chain = 'template',
    [ValidateRange(2048, 16384)]
    [int] $MemoryMiB = 4096
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Get-FullPath([string] $Path) {
    return [IO.Path]::GetFullPath($Path)
}

function Get-ChildPath([string] $Root, [string] $Relative) {
    $base = (Get-FullPath $Root).TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
    $path = Get-FullPath (Join-Path $base $Relative)
    if (-not $path.StartsWith($base, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'A metadata path escapes its expected directory.'
    }
    return $path
}

function Assert-File([string] $Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Missing installed dependency: $Path"
    }
}

function Read-VersionChain([string] $VersionFolder, [string[]] $Seen = @()) {
    if ($VersionFolder -notmatch '^[A-Za-z0-9_.-]+$' -or $VersionFolder -in $Seen) {
        throw 'Invalid or cyclic version inheritance.'
    }
    $path = Get-ChildPath $script:versionsRoot "$VersionFolder/$VersionFolder.json"
    Assert-File $path
    $data = Get-Content -Raw -LiteralPath $path | ConvertFrom-Json
    if ($data.inheritsFrom) {
        Read-VersionChain ([string] $data.inheritsFrom) (@($Seen) + $VersionFolder)
    }
    [pscustomobject] @{ Folder = $VersionFolder; Path = $path; Data = $data }
}

function Test-Rules($Rules) {
    if (-not $Rules -or @($Rules).Count -eq 0) { return $true }
    $allowed = $false
    foreach ($rule in $Rules) {
        $matchesRule = $true
        if ($rule.os) {
            if ($rule.os.name -and $rule.os.name -ne 'windows') { $matchesRule = $false }
            if ($rule.os.arch -and $rule.os.arch -ne 'amd64' -and $rule.os.arch -ne 'x86_64') {
                $matchesRule = $false
            }
            if ($rule.os.version -and [Environment]::OSVersion.Version.ToString() -notmatch $rule.os.version) {
                $matchesRule = $false
            }
        }
        if ($rule.features) {
            foreach ($feature in $rule.features.PSObject.Properties) {
                $value = $feature.Name -eq 'has_custom_resolution'
                if ($value -ne [bool] $feature.Value) { $matchesRule = $false }
            }
        }
        if ($matchesRule) { $allowed = $rule.action -eq 'allow' }
    }
    return $allowed
}

function Get-Arguments($Entries) {
    foreach ($entry in $Entries) {
        if ($entry -is [string]) { $entry }
        elseif (Test-Rules $entry.rules) {
            foreach ($value in @($entry.value)) { [string] $value }
        }
    }
}

function Expand-Argument([string] $Value, $Replacements) {
    foreach ($key in $Replacements.Keys) {
        $Value = $Value.Replace(('${' + $key + '}'), [string] $Replacements[$key])
    }
    if ($Value -match '\$\{[^}]+\}') { throw 'Unresolved version metadata argument placeholder.' }
    if ($Value -match '[\r\n\x00]') { throw 'Control character in launch argument.' }
    return $Value
}

function Quote-JavaArgument([string] $Value) {
    return '"' + $Value.Replace('\', '\\').Replace('"', '\"') + '"'
}

function Get-MavenPath([string] $Name) {
    $parts = $Name.Split(':')
    if ($parts.Count -lt 3 -or $parts.Count -gt 4) { throw 'Unsupported Maven library coordinate.' }
    $extension = 'jar'
    $last = $parts[$parts.Count - 1].Split('@')
    $parts[$parts.Count - 1] = $last[0]
    if ($last.Count -gt 1) { $extension = $last[1] }
    $suffix = ''
    if ($parts.Count -eq 4) { $suffix = '-' + $parts[3] }
    return $parts[0].Replace('.', '/') + '/' + $parts[1] + '/' + $parts[2] + '/' +
        $parts[1] + '-' + $parts[2] + $suffix + '.' + $extension
}

function Get-ModInfo([string] $JarPath, [string] $ExpectedId, [string] $ExpectedVersion) {
    Assert-File $JarPath
    $zip = [IO.Compression.ZipFile]::OpenRead($JarPath)
    try {
        $entry = $zip.GetEntry('META-INF/neoforge.mods.toml')
        if (-not $entry) { throw "Missing NeoForge metadata in $JarPath" }
        $reader = New-Object IO.StreamReader($entry.Open())
        try { $toml = $reader.ReadToEnd() } finally { $reader.Dispose() }
        if ($toml -notmatch ('(?m)^\s*modId\s*=\s*"' + [regex]::Escape($ExpectedId) + '"\s*$')) {
            throw "Unexpected mod identity in $JarPath"
        }
        if ($ExpectedVersion -and $toml -notmatch ('(?m)^\s*version\s*=\s*"' + [regex]::Escape($ExpectedVersion) + '"\s*$')) {
            throw "Unexpected mod version in $JarPath"
        }
    } finally { $zip.Dispose() }
    return [pscustomobject] @{ Path = $JarPath; Sha256 = (Get-FileHash -LiteralPath $JarPath -Algorithm SHA256).Hash }
}

if (-not [Environment]::Is64BitOperatingSystem -or $env:PROCESSOR_ARCHITECTURE -eq 'ARM64') {
    throw 'These installed PCL dependencies require Windows x64.'
}
$minecraftRoot = Get-ChildPath (Get-FullPath $PclRoot) '.minecraft'
$script:versionsRoot = Get-ChildPath $minecraftRoot 'versions'
$instanceName = 'AE2-lightoptimizer-' + $Generation
$gameDir = Get-ChildPath $script:versionsRoot $instanceName
$libraryDir = Get-ChildPath $minecraftRoot 'libraries'
$assetsDir = Get-ChildPath $minecraftRoot 'assets'
$EvidenceDir = Get-FullPath $EvidenceDir
# Keep generated artifacts outside every instance, especially its existing saves.
if (($EvidenceDir + '\').StartsWith(($script:versionsRoot + '\'), [StringComparison]::OrdinalIgnoreCase)) {
    throw 'EvidenceDir must be outside the PCL versions directory.'
}
$setupPath = Get-ChildPath $gameDir 'PCL/Setup.ini'
Assert-File $setupPath
if ((Get-Content -Raw -LiteralPath $setupPath) -notmatch '(?m)^VersionArgumentIndieV2:True\s*$') {
    throw 'The target must already use PCL independent-instance mode.'
}
$worldId = 'AE2LO-Ripper-Probe-' + $Generation + '-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff')
if (Test-Path -LiteralPath (Get-ChildPath $gameDir ('saves/' + $worldId))) {
    throw 'The generated probe world already exists; rerun to choose a fresh name.'
}
$versionChain = @(Read-VersionChain $instanceName)
$mainClass = $null
$assetIndex = $null
$requiredJava = 0
$clientRecord = $null
$libraries = [ordered] @{}
$jvmTemplates = New-Object 'Collections.Generic.List[string]'
$gameTemplates = New-Object 'Collections.Generic.List[string]'
foreach ($record in $versionChain) {
    $data = $record.Data
    if ($data.mainClass) { $mainClass = [string] $data.mainClass }
    if ($data.assetIndex) { $assetIndex = $data.assetIndex }
    if ($data.javaVersion) { $requiredJava = [int] $data.javaVersion.majorVersion }
    if ($data.downloads.client) { $clientRecord = $record }
    foreach ($arg in @(Get-Arguments $data.arguments.jvm)) { $jvmTemplates.Add($arg) }
    foreach ($arg in @(Get-Arguments $data.arguments.game)) { $gameTemplates.Add($arg) }
    foreach ($library in $data.libraries) {
        if (-not (Test-Rules $library.rules)) { continue }
        $parts = ([string] $library.name).Split(':')
        $key = $parts[0] + ':' + $parts[1]
        if ($parts.Count -gt 3) { $key += ':' + $parts[3] }
        $libraries[$key] = $library
    }
}
$expectedJava = if ($Generation -eq '1.21.1') { 21 } else { 25 }
if ($requiredJava -ne $expectedJava -or -not $mainClass -or -not $assetIndex -or -not $clientRecord) {
    throw 'Incomplete or unexpected installed version metadata.'
}
if (-not $JavaHome) {
    $envName = if ($requiredJava -eq 21) { 'AE2LIGHTOPTIMIZER_JAVA_HOME' } else { 'AE2LIGHTOPTIMIZER_JAVA25_HOME' }
    $JavaHome = [Environment]::GetEnvironmentVariable($envName)
    if (-not $JavaHome) {
        $JavaHome = Join-Path $env:USERPROFILE ('.gradle/jdks/eclipse_adoptium-' + $requiredJava + '-amd64-windows.2')
    }
}
$JavaHome = Get-FullPath $JavaHome
$releasePath = Get-ChildPath $JavaHome 'release'
Assert-File $releasePath
$release = Get-Content -Raw -LiteralPath $releasePath
if ($release -notmatch ('(?m)^JAVA_VERSION="' + $requiredJava + '\.')) {
    throw "Java $requiredJava is required by this instance. Supply -JavaHome or the generation's environment override."
}
$javaPath = Get-ChildPath $JavaHome 'bin/javaw.exe'
Assert-File $javaPath
$classpath = New-Object 'Collections.Generic.List[string]'
foreach ($library in $libraries.Values) {
    if ($library.natives) { throw 'Legacy native classifiers require an explicit launcher adapter.' }
    $relative = [string] $library.downloads.artifact.path
    if (-not $relative) { $relative = Get-MavenPath ([string] $library.name) }
    $path = Get-ChildPath $libraryDir $relative
    Assert-File $path
    if ($library.downloads.artifact.size -and (Get-Item -LiteralPath $path).Length -ne [long] $library.downloads.artifact.size) {
        throw "Installed library size differs from metadata: $path"
    }
    if (-not $classpath.Contains($path)) { $classpath.Add($path) }
}
# PCL aliases the 1.21 client JAR, while the 26 client remains in its inherited folder.
$clientJar = Get-ChildPath $script:versionsRoot ($clientRecord.Folder + '/' + $clientRecord.Folder + '.jar')
Assert-File $clientJar
if ((Get-FileHash -LiteralPath $clientJar -Algorithm SHA1).Hash -ne $clientRecord.Data.downloads.client.sha1) {
    throw 'The installed Minecraft client does not match its version metadata.'
}
$classpath.Add($clientJar)
$assetIndexPath = Get-ChildPath $assetsDir ('indexes/' + $assetIndex.id + '.json')
Assert-File $assetIndexPath
$productionPath = Get-ChildPath $gameDir ('mods/ae2lightoptimizer-neoforge-mc' + $Generation + '-0.0.4.jar')
$probePath = Get-ChildPath $gameDir ('mods/ae2lo-runtime-probe-mc' + $Generation + '-1.jar')
$production = Get-ModInfo $productionPath 'ae2lightoptimizer' '0.0.4'
$probe = $null
if (Test-Path -LiteralPath $probePath -PathType Leaf) {
    $probe = Get-ModInfo $probePath 'ae2lo_runtime_probe' ''
} elseif (-not $PrepareOnly) {
    throw "Install the isolated runtime probe JAR first: $probePath"
}
$otherProduction = @(Get-ChildItem -LiteralPath (Get-ChildPath $gameDir 'mods') -Filter 'ae2lightoptimizer*.jar' |
    Where-Object { $_.FullName -ne $productionPath })
if ($otherProduction.Count -gt 0) { throw 'More than one production AE2LO JAR is installed.' }

$nativesDir = Get-ChildPath $EvidenceDir 'natives'
# LWJGL and JNA extract the selected architecture from their native classifier JARs.
$replacements = @{
    natives_directory = $nativesDir; launcher_name = 'AE2LO-runtime-probe'; launcher_version = '1';
    classpath = ($classpath -join ';'); classpath_separator = ';'; library_directory = $libraryDir;
    version_name = $instanceName; game_directory = $gameDir; assets_root = $assetsDir;
    assets_index_name = [string] $assetIndex.id; auth_player_name = 'AE2LOProbe';
    auth_uuid = '9491f088c3c333b7b626bcfc43f28f39'; auth_access_token = '0';
    clientid = ''; auth_xuid = ''; user_type = 'legacy'; version_type = 'release';
    user_properties = '{}'; resolution_width = '1280'; resolution_height = '720'
}
$jvmArgs = New-Object 'Collections.Generic.List[string]'
$jvmArgs.Add('-Xms512M')
$jvmArgs.Add('-Xmx' + $MemoryMiB + 'M')
foreach ($arg in $jvmTemplates) { $jvmArgs.Add((Expand-Argument $arg $replacements)) }
foreach ($arg in @('-Dae2lo.probe=true', '-Dae2lo.probe.autoExit=true',
    ('-Dae2lo.probe.chain=' + $Chain),
    ('-Dae2lo.probe.reportDir=' + $EvidenceDir), ('-Dae2lo.probe.output=' + $EvidenceDir),
    ('-Dae2lo.probe.worldId=' + $worldId), ('-Dae2lo.probe.world=' + $worldId))) {
    $jvmArgs.Add($arg)
}
for ($i = 0; $i -lt $jvmArgs.Count; $i++) {
    if ($jvmArgs[$i] -in @('-p', '--module-path', '-cp', '-classpath', '--class-path')) {
        if ($i + 1 -ge $jvmArgs.Count) { throw 'Missing classpath or module path argument.' }
        foreach ($path in $jvmArgs[$i + 1].Split(';')) { Assert-File $path }
    }
}
$gameArgs = New-Object 'Collections.Generic.List[string]'
# Remove authentication fields before expansion so account-shaped literal metadata
# can never enter the argument file. No PCL account or cached batch file is read.
$authFlags = @('--username', '--uuid', '--accessToken', '--clientId', '--xuid', '--userType',
    '--userProperties', '--profileKeys', '--profileKey', '--proxyUser', '--proxyPass')
for ($i = 0; $i -lt $gameTemplates.Count; $i++) {
    $arg = $gameTemplates[$i]
    $flag = $arg.Split('=')[0]
    if ($flag -in $authFlags) {
        if (-not $arg.Contains('=')) { $i++ }
        continue
    }
    $gameArgs.Add((Expand-Argument $arg $replacements))
}
foreach ($arg in @('--username', 'AE2LOProbe', '--uuid', $replacements.auth_uuid,
    '--accessToken', '0', '--userType', 'legacy')) { $gameArgs.Add($arg) }
if ($gameArgs.Contains('--quickPlaySingleplayer')) {
    throw 'The probe must create its own new world, not use Quick Play to open an existing save.'
}

[IO.Directory]::CreateDirectory($EvidenceDir) | Out-Null
[IO.Directory]::CreateDirectory($nativesDir) | Out-Null
$argumentFile = Get-ChildPath $EvidenceDir 'java-arguments.txt'
$stdoutPath = Get-ChildPath $EvidenceDir 'client-stdout.log'
$stderrPath = Get-ChildPath $EvidenceDir 'client-stderr.log'
$planPath = Get-ChildPath $EvidenceDir 'launch-plan.json'
foreach ($outputPath in @($argumentFile, $stdoutPath, $stderrPath, $planPath)) {
    if (Test-Path -LiteralPath $outputPath) { throw 'EvidenceDir already contains a launch; use a fresh evidence directory.' }
}
$allArgs = @($jvmArgs.ToArray()) + @($mainClass) + @($gameArgs.ToArray())
$encodedArgs = @($allArgs | ForEach-Object { Quote-JavaArgument $_ })
$utf8 = New-Object Text.UTF8Encoding($false)
[IO.File]::WriteAllLines($argumentFile, [string[]] $encodedArgs, $utf8)
$plan = [ordered] @{
    Generation = $Generation; Chain = $Chain; PreparedOnly = [bool] $PrepareOnly; Java = $javaPath;
    JavaMajor = $requiredJava; MainClass = $mainClass; GameDirectory = $gameDir;
    WorldId = $worldId; EvidenceDirectory = $EvidenceDir; Identity = 'AE2LOProbe (offline)';
    ClasspathCount = $classpath.Count; ClientJar = $clientJar; Metadata = @($versionChain.Path);
    ArgumentFile = $argumentFile; Stdout = $stdoutPath; Stderr = $stderrPath;
    Production = $production; Probe = $probe; ExpectedProbe = $probePath
}
[IO.File]::WriteAllText($planPath, ($plan | ConvertTo-Json -Depth 6), $utf8)
if ($PrepareOnly) {
    [pscustomobject] @{ Prepared = $true; Generation = $Generation; JavaMajor = $requiredJava;
        ClasspathCount = $classpath.Count; ProbeInstalled = $null -ne $probe; EvidenceDir = $EvidenceDir }
    return
}
# Minecraft is intentionally visible; javaw avoids opening an extra console.
$process = Start-Process -FilePath $javaPath -ArgumentList ('"@' + $argumentFile + '"') -WorkingDirectory $gameDir `
    -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath -WindowStyle Normal -PassThru
$plan['ProcessId'] = $process.Id
$plan['StartedAt'] = [DateTimeOffset]::Now.ToString('o')
[IO.File]::WriteAllText($planPath, ($plan | ConvertTo-Json -Depth 6), $utf8)
[pscustomobject] @{ ProcessId = $process.Id; EvidenceDir = $EvidenceDir }
