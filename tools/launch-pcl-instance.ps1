#requires -Version 5.1
<#
.SYNOPSIS
Launch an installed PCL instance exactly as PCL would, without the probe and without PCL account data.
.DESCRIPTION
Reads the installed version metadata (instance + inherited parents), builds the classpath from the
installed libraries, and starts the client with an offline identity. Logs go to EvidenceDir. The
process is left running; stop it normally with the window close button or -StopAfterSeconds.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][ValidateSet('1.21.1', '26.1.2')][string] $Generation,
    [Parameter(Mandatory = $true)][string] $EvidenceDir,
    [string] $PclRoot = (Join-Path $env:USERPROFILE 'Desktop/Files/Minecraft/PCL'),
    [string] $JavaHome,
    [ValidateRange(2048, 16384)][int] $MemoryMiB = 4096,
    [int] $StopAfterSeconds = 0
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Get-FullPath([string] $Path) { [IO.Path]::GetFullPath($Path) }
function Get-ChildPath([string] $Root, [string] $Relative) {
    $base = (Get-FullPath $Root).TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
    $path = Get-FullPath (Join-Path $base $Relative)
    if (-not $path.StartsWith($base, [StringComparison]::OrdinalIgnoreCase)) { throw 'A metadata path escapes its expected directory.' }
    $path
}
function Assert-File([string] $Path) { if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw "Missing file: $Path" } }
function Test-Rules($Rules) {
    if (-not $Rules -or @($Rules).Count -eq 0) { return $true }
    $allowed = $false
    foreach ($rule in $Rules) {
        $matchesRule = $true
        if ($rule.os) {
            if ($rule.os.name -and $rule.os.name -ne 'windows') { $matchesRule = $false }
            if ($rule.os.arch -and $rule.os.arch -ne 'amd64' -and $rule.os.arch -ne 'x86_64') { $matchesRule = $false }
        }
        if ($rule.features) {
            foreach ($feature in $rule.features.PSObject.Properties) {
                $value = $feature.Name -eq 'has_custom_resolution'
                if ($value -ne [bool] $feature.Value) { $matchesRule = $false }
            }
        }
        if ($matchesRule) { $allowed = $rule.action -eq 'allow' }
    }
    $allowed
}
function Get-Arguments($Entries) {
    foreach ($entry in $Entries) {
        if ($entry -is [string]) { $entry }
        elseif (Test-Rules $entry.rules) { foreach ($value in @($entry.value)) { [string] $value } }
    }
}
function Get-MavenPath([string] $Name) {
    $parts = $Name.Split(':')
    $extension = 'jar'
    $last = $parts[$parts.Count - 1].Split('@')
    $parts[$parts.Count - 1] = $last[0]
    if ($last.Count -gt 1) { $extension = $last[1] }
    $suffix = ''
    if ($parts.Count -eq 4) { $suffix = '-' + $parts[3] }
    $parts[0].Replace('.', '/') + '/' + $parts[1] + '/' + $parts[2] + '/' + $parts[1] + '-' + $parts[2] + $suffix + '.' + $extension
}
function Expand-Argument([string] $Value, $Replacements) {
    foreach ($key in $Replacements.Keys) { $Value = $Value.Replace(('${' + $key + '}'), [string] $Replacements[$key]) }
    if ($Value -match '\$\{[^}]+\}') { throw "Unresolved metadata placeholder: $Value" }
    $Value
}
function Quote-JavaArgument([string] $Value) { '"' + $Value.Replace('\', '\\').Replace('"', '\"') + '"' }

$minecraftRoot = Get-ChildPath (Get-FullPath $PclRoot) '.minecraft'
$script:versionsRoot = Get-ChildPath $minecraftRoot 'versions'
$instanceName = 'AE2 LoopTech-' + $Generation
$gameDir = Get-ChildPath $script:versionsRoot $instanceName
$libraryDir = Get-ChildPath $minecraftRoot 'libraries'
$assetsDir = Get-ChildPath $minecraftRoot 'assets'

function Read-VersionChain([string] $Folder, [string[]] $Seen = @()) {
    $path = Get-ChildPath $script:versionsRoot ("$Folder/$Folder.json")
    Assert-File $path
    $data = Get-Content -Raw -LiteralPath $path | ConvertFrom-Json
    if ($data.inheritsFrom) { Read-VersionChain ([string] $data.inheritsFrom) (@($Seen) + $Folder) }
    [pscustomobject] @{ Folder = $Folder; Path = $path; Data = $data }
}

$chain = @(Read-VersionChain $instanceName)
$mainClass = $null; $assetIndex = $null; $requiredJava = 0; $clientRecord = $null
$libraries = [ordered] @{}
$jvmTemplates = New-Object 'Collections.Generic.List[string]'
$gameTemplates = New-Object 'Collections.Generic.List[string]'
foreach ($record in $chain) {
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
if (-not $mainClass -or -not $assetIndex -or -not $clientRecord -or $requiredJava -eq 0) { throw 'Incomplete version metadata.' }
if (-not $JavaHome) { $JavaHome = Join-Path $env:USERPROFILE ('.gradle/jdks/eclipse_adoptium-' + $requiredJava + '-amd64-windows.2') }
$javaPath = Get-ChildPath $JavaHome 'bin/javaw.exe'
Assert-File $javaPath
$classpath = New-Object 'Collections.Generic.List[string]'
foreach ($library in $libraries.Values) {
    if ($library.natives) { throw 'Legacy native classifiers are not supported by this launcher.' }
    $relative = [string] $library.downloads.artifact.path
    if (-not $relative) { $relative = Get-MavenPath ([string] $library.name) }
    $path = Get-ChildPath $libraryDir $relative
    Assert-File $path
    if (-not $classpath.Contains($path)) { $classpath.Add($path) }
}
$clientJar = Get-ChildPath $script:versionsRoot ($clientRecord.Folder + '/' + $clientRecord.Folder + '.jar')
Assert-File $clientJar
$classpath.Add($clientJar)
$assetIndexPath = Get-ChildPath $assetsDir ('indexes/' + $assetIndex.id + '.json')
Assert-File $assetIndexPath

$EvidenceDir = Get-FullPath $EvidenceDir
if (($EvidenceDir + '\').StartsWith(($script:versionsRoot + '\'), [StringComparison]::OrdinalIgnoreCase)) { throw 'EvidenceDir must stay outside the versions directory.' }
[IO.Directory]::CreateDirectory($EvidenceDir) | Out-Null
$nativesDir = Get-ChildPath $EvidenceDir 'natives'
[IO.Directory]::CreateDirectory($nativesDir) | Out-Null
$argumentFile = Get-ChildPath $EvidenceDir 'java-arguments.txt'
$stdoutPath = Get-ChildPath $EvidenceDir 'client-stdout.log'
$stderrPath = Get-ChildPath $EvidenceDir 'client-stderr.log'
foreach ($outputPath in @($argumentFile, $stdoutPath, $stderrPath)) { if (Test-Path -LiteralPath $outputPath) { throw 'EvidenceDir already contains a launch.' } }

$replacements = @{
    natives_directory = $nativesDir; launcher_name = 'AE2LF-instance-launch'; launcher_version = '1'
    classpath = ($classpath -join ';'); classpath_separator = ';'; library_directory = $libraryDir
    version_name = $instanceName; game_directory = $gameDir; assets_root = $assetsDir
    assets_index_name = [string] $assetIndex.id; auth_player_name = 'AE2LFCheck'
    auth_uuid = '9491f088c3c333b7b626bcfc43f28f39'; auth_access_token = '0'
    clientid = ''; auth_xuid = ''; user_type = 'legacy'; version_type = 'release'
    user_properties = '{}'; resolution_width = '1280'; resolution_height = '720'
}
$jvmArgs = New-Object 'Collections.Generic.List[string]'
$jvmArgs.Add('-Xms512M'); $jvmArgs.Add('-Xmx' + $MemoryMiB + 'M')
foreach ($arg in $jvmTemplates) { $jvmArgs.Add((Expand-Argument $arg $replacements)) }
$gameArgs = New-Object 'Collections.Generic.List[string]'
$authFlags = @('--username', '--uuid', '--accessToken', '--clientId', '--xuid', '--userType', '--userProperties', '--profileKeys', '--profileKey', '--proxyUser', '--proxyPass', '--quickPlayPath', '--quickPlaySingleplayer', '--quickPlayMultiplayer', '--quickPlayRealms')
for ($i = 0; $i -lt $gameTemplates.Count; $i++) {
    $arg = $gameTemplates[$i]
    if ($arg.Split('=')[0] -in $authFlags) { if (-not $arg.Contains('=')) { $i++ }; continue }
    $gameArgs.Add((Expand-Argument $arg $replacements))
}
foreach ($arg in @('--username', 'AE2LFCheck', '--uuid', $replacements.auth_uuid, '--accessToken', '0', '--userType', 'legacy')) { $gameArgs.Add($arg) }
$allArgs = @($jvmArgs.ToArray()) + @($mainClass) + @($gameArgs.ToArray())
$utf8 = New-Object Text.UTF8Encoding($false)
[IO.File]::WriteAllLines($argumentFile, [string[]] @($allArgs | ForEach-Object { Quote-JavaArgument $_ }), $utf8)
$process = Start-Process -FilePath $javaPath -ArgumentList ('"@' + $argumentFile + '"') -WorkingDirectory $gameDir -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath -WindowStyle Normal -PassThru
[pscustomobject] @{ ProcessId = $process.Id; GameDirectory = $gameDir; EvidenceDir = $EvidenceDir; Java = $javaPath; MainClass = $mainClass; ClasspathCount = $classpath.Count; Stdout = $stdoutPath; Stderr = $stderrPath } | ConvertTo-Json -Depth 4
if ($StopAfterSeconds -gt 0) {
    Start-Sleep -Seconds $StopAfterSeconds
    Stop-Process -Id $process.Id -ErrorAction SilentlyContinue
}
