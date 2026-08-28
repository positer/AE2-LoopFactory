$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$env:GRADLE_USER_HOME = Join-Path $root '.gradle-user-home/26.1.2'
# Gradle 8.12 runs on Java 21; ModDev selects the Java 25 compilation toolchain.
$cachedJava21Home = Join-Path $env:USERPROFILE '.gradle\jdks\eclipse_adoptium-21-amd64-windows.2'
$cachedJava25Home = Join-Path $env:USERPROFILE '.gradle\jdks\eclipse_adoptium-25-amd64-windows.2'
if ($env:AE2LIGHTOPTIMIZER_JAVA_HOME) {
    $env:JAVA_HOME = $env:AE2LIGHTOPTIMIZER_JAVA_HOME
} elseif (Test-Path (Join-Path $cachedJava21Home 'bin\java.exe')) {
    $env:JAVA_HOME = $cachedJava21Home
} elseif (-not $env:JAVA_HOME) {
    throw 'Set AE2LIGHTOPTIMIZER_JAVA_HOME or JAVA_HOME to a JDK 21 installation.'
}

$toolchainHomes = @(
    $env:AE2LIGHTOPTIMIZER_JAVA_HOME
    $env:AE2LIGHTOPTIMIZER_JAVA25_HOME
    $cachedJava21Home
    $cachedJava25Home
) | Where-Object { $_ -and (Test-Path (Join-Path $_ 'bin\java.exe')) } | Select-Object -Unique
$toolchainArgument = "-Porg.gradle.java.installations.paths=$($toolchainHomes -join ',')"

& (Join-Path $root 'versions/neoforge-26.1.2/gradlew.bat') --project-dir (Join-Path $root 'versions/neoforge-26.1.2') $toolchainArgument build @args
exit $LASTEXITCODE
