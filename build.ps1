param([switch]$Personal,[switch]$Checks)
$ErrorActionPreference='Stop'
Set-Location $PSScriptRoot
$toolBase='C:\Users\m.abdellatif\Documents\Codex\2026-11-07\apk-mirror\.tools'
$env:JAVA_HOME=(Get-ChildItem "$toolBase/jdk-build" -Directory | Select-Object -First 1).FullName
$env:GRADLE_USER_HOME=Join-Path $PSScriptRoot '.tools/gradle-cache'
$env:ANDROID_USER_HOME=Join-Path $PSScriptRoot '.tools/android'
$env:MAGDI_STORE_PASSWORD=(Get-Content '.private/signing/store-password.txt' -Raw).Trim()
$env:MAGDI_INCLUDE_TEST_ACCOUNT=if($Personal){'1'}else{'0'}
('sdk.dir='+"$toolBase/sdk".Replace('\','/')) | Set-Content local.properties
$seed=Join-Path $PSScriptRoot 'app/build/generated/privateAssets/initial-account.json'
if((Test-Path -LiteralPath $seed) -and !$Personal){Remove-Item -LiteralPath $seed}
try {
 $tasks=@(':app:assembleRelease',':app:assembleDebug')
 if($Checks){$tasks+=@(':backend:check',':app:lintRelease',':app:testDebugUnitTest')}
 & "$toolBase/gradle/gradle-8.11.1/bin/gradle.bat" --no-daemon @tasks
 if($LASTEXITCODE -ne 0){throw 'Build or checks failed'}
 $name=if($Personal){'MM-IPTV-0.1.13-personal.apk'}else{'MM-IPTV-0.1.13.apk'}
 Copy-Item app/build/outputs/apk/release/app-release.apk "dist/$name"
 & "$toolBase/sdk/build-tools/36.0.0/apksigner.bat" verify --print-certs "dist/$name"
 if($LASTEXITCODE -ne 0){throw 'APK signature invalid'}
 Get-FileHash "dist/$name" -Algorithm SHA256
} finally {Remove-Item Env:MAGDI_STORE_PASSWORD -ErrorAction SilentlyContinue}
