# ============================================================
#  Compile et package le plugin HardcoreShared
#  Usage : powershell -ExecutionPolicy Bypass -File build.ps1
# ============================================================
$ErrorActionPreference = 'Stop'
$root   = $PSScriptRoot
$jdk    = Join-Path $root 'jdk\bin'
$api    = Join-Path $root 'plugin\paper-api.jar'
$src    = Join-Path $root 'plugin\src\main\java'
$res    = Join-Path $root 'plugin\src\main\resources'
$out    = Join-Path $root 'plugin\build\classes'
$jarOut = Join-Path $root 'server\plugins\HardcoreShared.jar'

if (-not (Test-Path $api)) { throw "paper-api.jar introuvable : $api" }

# Classpath = paper-api + toutes les libs du serveur (Adventure, Bungee chat, etc.)
$libDir = Join-Path $root 'server\libraries'
$cp = $api
if (Test-Path $libDir) {
    $libs = Get-ChildItem $libDir -Recurse -Filter *.jar | ForEach-Object { $_.FullName }
    if ($libs) { $cp = ($api + ';' + ($libs -join ';')) }
}

if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Force -Path $out | Out-Null

$sources = Get-ChildItem -Path $src -Recurse -Filter *.java | ForEach-Object { $_.FullName }
Write-Host "Compilation de $($sources.Count) fichiers Java..."

& "$jdk\javac.exe" -cp $cp -d $out --release 21 -nowarn $sources
if ($LASTEXITCODE -ne 0) { throw "Echec de la compilation" }

# Ressources (plugin.yml, config.yml) dans le jar
Copy-Item (Join-Path $res '*') $out -Recurse -Force

if (Test-Path $jarOut) { Remove-Item $jarOut -Force }
& "$jdk\jar.exe" --create --file $jarOut -C $out .
if ($LASTEXITCODE -ne 0) { throw "Echec du packaging jar" }

Write-Host "OK -> $jarOut"
