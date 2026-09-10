$ErrorActionPreference = "Stop"
$jdk = "C:\Program Files\Java\jdk-26.0.2\bin"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Push-Location $root

$files = Get-ChildItem -Path "C:\CLLL\libraries", "C:\CLLL\plugins" -Filter "*.jar" -Recurse |
    Select-Object -ExpandProperty FullName
$cp = $files -join ";"

New-Item -ItemType Directory -Path build\classes -Force | Out-Null
New-Item -ItemType Directory -Path build\test-classes -Force | Out-Null
Remove-Item -Path build\classes\* -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -Path build\test-classes\* -Recurse -Force -ErrorAction SilentlyContinue

$mainSources = Get-ChildItem -Path src\main\java -Filter "*.java" -Recurse |
    Select-Object -ExpandProperty FullName
& "$jdk\javac.exe" --release 25 -encoding UTF-8 -cp "$cp" -d build\classes $mainSources
if ($LASTEXITCODE -ne 0) { Pop-Location; exit 1 }

$testSources = Get-ChildItem -Path src\test\java -Filter "*.java" -Recurse -ErrorAction SilentlyContinue |
    Select-Object -ExpandProperty FullName
if ($testSources) {
    & "$jdk\javac.exe" --release 25 -encoding UTF-8 -cp "$cp;build\classes" -d build\test-classes $testSources
    if ($LASTEXITCODE -ne 0) { Pop-Location; exit 1 }
}

Copy-Item src\main\resources\* build\classes\ -Force -Recurse
Push-Location build\classes
& "$jdk\jar.exe" cf ..\..\SunshineCommandGuard-1.4.1.jar *
Pop-Location
if ($LASTEXITCODE -ne 0) { Pop-Location; exit 1 }

Write-Host "BUILD_OK"
Pop-Location
