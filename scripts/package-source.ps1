$ErrorActionPreference='Stop'
Set-Location (Split-Path $PSScriptRoot)
Add-Type -AssemblyName System.IO.Compression.FileSystem
$destination=Join-Path (Get-Location) 'dist/Magdi-IPTV-0.1.5-source.zip'
$entries=@('app/src','app/libs','app/build.gradle','backend/src','backend/build.gradle','gradle','gradlew','gradlew.bat','licenses','scripts','tests','README.md','VALIDATION.md','PICKER-FIX.md','build.gradle','settings.gradle','gradle.properties','build.ps1','start-browser-test.ps1','test-emulator.ps1','.gitignore','.gitattributes')
$output=[IO.File]::Open($destination,[IO.FileMode]::Create)
$archive=[IO.Compression.ZipArchive]::new($output,[IO.Compression.ZipArchiveMode]::Create)
try {
 foreach($entry in $entries){
  $files=if(Test-Path $entry -PathType Container){Get-ChildItem $entry -File -Recurse}else{Get-Item $entry}
  foreach($file in $files){
   $name=[IO.Path]::GetRelativePath((Get-Location).Path,$file.FullName).Replace('\','/')
   [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,$file.FullName,$name,[IO.Compression.CompressionLevel]::Optimal)|Out-Null
  }
 }
} finally {$archive.Dispose()}
Write-Output 'Source package created from explicit allowlist; private inputs and build outputs excluded.'
