# ============================================================
#  Setup — to run ONCE after cloning the repo from GitHub.
#  Downloads everything excluded from git (JDK 25, Paper server,
#  Paper API) and builds the plugin, so start.bat works.
#
#  Usage:  powershell -ExecutionPolicy Bypass -File setup.ps1
# ============================================================
$ErrorActionPreference = 'Stop'
$ProgressPreference     = 'SilentlyContinue'
$root = $PSScriptRoot

function Step($m) { Write-Host "`n==> $m" -ForegroundColor Cyan }

# ---- 1. JDK 25 (Temurin) -----------------------------------
Step "Java 25 (Temurin)"
if (Test-Path "$root\jdk\bin\java.exe") {
    Write-Host "    deja present, on saute."
} else {
    $asset = Invoke-RestMethod "https://api.adoptium.net/v3/assets/latest/25/hotspot?os=windows&architecture=x64&image_type=jdk&vendor=eclipse"
    $url = $asset[0].binary.package.link
    Write-Host "    telechargement: $url"
    Invoke-WebRequest $url -OutFile "$root\jdk.zip"
    Expand-Archive "$root\jdk.zip" -DestinationPath "$root\jdk_tmp" -Force
    $inner = (Get-ChildItem "$root\jdk_tmp" -Directory)[0].FullName
    if (Test-Path "$root\jdk") { Remove-Item "$root\jdk" -Recurse -Force }
    Move-Item $inner "$root\jdk"
    Remove-Item "$root\jdk_tmp","$root\jdk.zip" -Recurse -Force
}
$java = "$root\jdk\bin\java.exe"
& $java -version

# ---- 2. Paper server (latest 26.1.2 build) -----------------
Step "Serveur Paper 26.1.2"
$h = @{ 'User-Agent' = 'HardcoreSetup/1.0' }
$builds = Invoke-RestMethod "https://fill.papermc.io/v3/projects/paper/versions/26.1.2/builds" -Headers $h
$latest = $builds | Sort-Object id -Descending | Select-Object -First 1
$build  = $latest.id
$dl     = $latest.downloads.'server:default'.url
Write-Host "    build $build"
Invoke-WebRequest $dl -OutFile "$root\server\paper.jar" -Headers $h
if (-not (Test-Path "$root\server\eula.txt")) {
    Set-Content "$root\server\eula.txt" "eula=true" -Encoding ascii
}

# ---- 3. Paper API matching the build (for compilation) -----
Step "Paper API (compilation)"
$apiVer = "26.1.2.build.$build-stable"
$apiUrl = "https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/$apiVer/paper-api-$apiVer.jar"
Invoke-WebRequest $apiUrl -OutFile "$root\plugin\paper-api.jar"

# ---- 4. First run: generate libraries/ + versions/ + config -
Step "Premier demarrage (genere libs + configs)"
$p = Start-Process -FilePath $java `
        -ArgumentList @('-Xms1G','-Xmx2G','-jar','paper.jar','--nogui') `
        -WorkingDirectory "$root\server" `
        -RedirectStandardOutput "$root\server\setup-firstrun.log" `
        -RedirectStandardError  "$root\server\setup-firstrun.err.log" `
        -PassThru
Write-Host "    PID $($p.Id) — attente du demarrage..."
$ready = $false
for ($i = 0; $i -lt 150; $i++) {
    Start-Sleep 2
    if (Select-String -Path "$root\server\setup-firstrun.log" -Pattern 'Done \(' -Quiet -ErrorAction SilentlyContinue) {
        $ready = $true; break
    }
}
try { Stop-Process -Id $p.Id -Force -ErrorAction Stop } catch {}
Start-Sleep 3
if (-not $ready) { throw "Le serveur n'a pas fini de demarrer — voir server\setup-firstrun.log" }
Remove-Item "$root\server\setup-firstrun.log","$root\server\setup-firstrun.err.log" -ErrorAction SilentlyContinue

# ---- 5. Build the plugin -----------------------------------
Step "Compilation du plugin"
& powershell -ExecutionPolicy Bypass -File "$root\build.ps1"

Write-Host "`n=============================================" -ForegroundColor Green
Write-Host " Setup termine. Lance le serveur : start.bat" -ForegroundColor Green
Write-Host "=============================================" -ForegroundColor Green
