$ErrorActionPreference='Stop'
Set-Location (Split-Path $PSScriptRoot)
Add-Type -AssemblyName System.IO.Compression.FileSystem
$account=Get-Content '.private/provider.json' -Raw | ConvertFrom-Json
$secrets=@($account.username,$account.password,$account.host) | Where-Object {$_}
$results=@()
foreach($file in Get-ChildItem dist -File | Where-Object {$_.Extension -in '.apk','.zip'}){
 $archive=[IO.Compression.ZipFile]::OpenRead($file.FullName)
 try {
  $seed=$false;$found=$false;$privatePath=$false
  foreach($entry in $archive.Entries){
   if($entry.FullName -eq 'assets/initial-account.json'){$seed=$true}
   if($file.Extension -eq '.zip' -and $entry.FullName -match '(^|/)(\.private|build|\.tools)/'){$privatePath=$true}
   $stream=$entry.Open();$reader=[IO.StreamReader]::new($stream)
   try{$content=$reader.ReadToEnd();foreach($secret in $secrets){if($content.Contains([string]$secret)){$found=$true}}}finally{$reader.Dispose()}
  }
  $personal=$file.Name -like '*-personal.apk'
  if($personal -and (!$seed -or !$found)){throw 'Personal account provisioning missing'}
  if(!$personal -and ($seed -or $found -or $privatePath)){throw 'Unexpected private data in clean artifact'}
  $results+=@{file=$file.Name;bytes=$file.Length;sha256=(Get-FileHash $file.FullName -Algorithm SHA256).Hash;accountProvisioning=$seed;privateValuesPresent=$found}
 }finally{$archive.Dispose()}
}
$results|ConvertTo-Json|Set-Content dist/verification.json
$results|ConvertTo-Json
