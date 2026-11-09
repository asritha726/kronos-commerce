# Apex OMS - Order & Inventory Management System Launcher
Write-Host "=========================================================" -ForegroundColor White
Write-Host "   Apex OMS - Order & Inventory Management System        " -ForegroundColor White
Write-Host "=========================================================" -ForegroundColor White

$javac = "C:\Program Files\Java\jdk-26.0.2\bin\javac.exe"
$java = "C:\Program Files\Java\jdk-26.0.2\bin\java.exe"

if (-not (Test-Path $javac)) {
    $javac = "javac"
    $java = "java"
}

if (-not (Test-Path "backend\bin")) {
    New-Item -ItemType Directory -Force -Path "backend\bin" | Out-Null
}

Write-Host "[*] Compiling Java backend..." -ForegroundColor Gray
& $javac -d "backend/bin" "@sources.txt"

if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Compilation failed!" -ForegroundColor Red
    exit 1
}

Write-Host "[OK] Compiled successfully." -ForegroundColor Gray
Write-Host "[*] Starting server at http://localhost:8080 ...`n" -ForegroundColor White

& $java -cp "backend/bin" com.fintech.oms.Main

