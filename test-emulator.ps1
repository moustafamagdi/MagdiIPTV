param([ValidateSet('Start','Status','Stop')][string]$Mode='Start')
$ErrorActionPreference='Stop'
Set-Location $PSScriptRoot
$sdk='C:\Users\m.abdellatif\Documents\Codex\2026-11-07\apk-mirror\.tools\sdk'
$env:ANDROID_HOME=$sdk
$env:ANDROID_USER_HOME=Join-Path $PSScriptRoot '.tools/android'
$env:ANDROID_AVD_HOME=Join-Path $PSScriptRoot '.tools/avd'
$env:ANDROID_EMULATOR_HOME=Join-Path $PSScriptRoot '.tools/emulator-home'
$env:ANDROID_ADB_SERVER_PORT='5038'
$env:ANDROID_I_WANT_MY_TCG='yes'
New-Item -ItemType Directory -Force $env:ANDROID_USER_HOME,$env:ANDROID_AVD_HOME,$env:ANDROID_EMULATOR_HOME | Out-Null
if($Mode -eq 'Status'){& "$sdk/platform-tools/adb.exe" -P 5038 devices;Get-Content logs/emulator-error.txt -Tail 12 -ErrorAction SilentlyContinue;exit}
if($Mode -eq 'Stop'){& "$sdk/platform-tools/adb.exe" -P 5038 -s emulator-5560 emu kill;exit}
$avd=Join-Path $env:ANDROID_AVD_HOME 'MagdiIPTV35.avd'
New-Item -ItemType Directory -Force $avd | Out-Null
@"
avd.ini.encoding=UTF-8
path=$avd
target=android-36
"@ | Set-Content (Join-Path $env:ANDROID_AVD_HOME 'MagdiIPTV35.ini')
@"
AvdId=MagdiIPTV35
avd.ini.displayname=Magdi IPTV Test
abi.type=x86_64
hw.cpu.arch=x86_64
hw.cpu.ncore=4
hw.ramSize=2048
hw.lcd.width=720
hw.lcd.height=1280
hw.lcd.density=240
hw.keyboard=yes
hw.gpu.enabled=yes
hw.gpu.mode=swiftshader
image.sysdir.1=$($sdk.Replace('\','/'))/system-images/android-36/default/x86_64/
tag.id=default
tag.display=Default
disk.dataPartition.size=2048M
showDeviceFrame=no
"@ | Set-Content (Join-Path $avd 'config.ini')
& "$sdk/platform-tools/adb.exe" -P 5038 start-server
$p=Start-Process -FilePath (Join-Path $PSScriptRoot '.tools/emulator35/emulator/emulator.exe') -ArgumentList '-avd','MagdiIPTV35','-no-window','-no-audio','-no-snapshot','-no-boot-anim','-gpu','swiftshader_indirect','-feature','-Vulkan','-accel','on','-cores','1','-memory','1536','-show-kernel','-port','5560','-no-metrics' -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $PSScriptRoot 'logs/emulator-output.txt') -RedirectStandardError (Join-Path $PSScriptRoot 'logs/emulator-error.txt')
Write-Output "Emulator process $($p.Id) started"
$p.WaitForExit()
Write-Output "Emulator exit code: $($p.ExitCode)"
