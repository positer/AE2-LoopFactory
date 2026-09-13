$ErrorActionPreference='Stop'
$taskRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$agentRoot=Join-Path $PSScriptRoot 'background-agent'
$asm=Join-Path $taskRoot '.gradle-user-home/1.21.1/caches/modules-2/files-2.1/org.ow2.asm/asm/9.9.1/2ceea6ab43bcae1979b2a6d85fc0ca429877e5ab/asm-9.9.1.jar'
Copy-Item -LiteralPath $asm -Destination (Join-Path $agentRoot 'asm.jar')
$javaBin=Join-Path $env:USERPROFILE '.gradle/jdks/eclipse_adoptium-21-amd64-windows.2/bin'
& (Join-Path $javaBin 'javac.exe') --release 17 -classpath (Join-Path $agentRoot 'asm.jar') -d (Join-Path $agentRoot 'classes') (Join-Path $agentRoot 'HiddenWindowAgent.java')
if($LASTEXITCODE -ne 0){throw 'Agent compilation failed'}
& (Join-Path $javaBin 'jar.exe') cfm (Join-Path $agentRoot 'hidden-window-agent.jar') (Join-Path $agentRoot 'MANIFEST.MF') -C (Join-Path $agentRoot 'classes') .
if($LASTEXITCODE -ne 0){throw 'Agent packaging failed'}
Write-Output 'Built isolated invisible-window agent for Java 21/25; no production JAR is changed.'
