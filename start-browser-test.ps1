$ErrorActionPreference='Stop'
Set-Location $PSScriptRoot
$profile=Join-Path $PSScriptRoot '.tools/chrome-test'
$chrome='C:\Program Files\Google\Chrome\Application\chrome.exe'
$arguments=@('--headless=new','--disable-gpu','--no-first-run','--no-default-browser-check','--remote-debugging-port=9224',('--user-data-dir="'+$profile+'"'),'--autoplay-policy=no-user-gesture-required','--window-size=1280,900','http://127.0.0.1:8787/')
$p=Start-Process $chrome -ArgumentList $arguments -WindowStyle Hidden -PassThru -RedirectStandardOutput logs/chrome-output.txt -RedirectStandardError logs/chrome-error.txt
Write-Output "Test browser $($p.Id) started"
$p.WaitForExit()
