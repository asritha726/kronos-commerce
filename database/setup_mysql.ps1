# Apex OMS - MySQL Database Setup
Write-Host "=========================================================" -ForegroundColor White
Write-Host "   Apex OMS - MySQL Database Setup                       " -ForegroundColor White
Write-Host "=========================================================" -ForegroundColor White

$mysqlBin = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
if (-not (Test-Path $mysqlBin)) {
    $mysqlBin = "mysql"
}

Write-Host "[*] Importing database\schema.sql into MySQL..." -ForegroundColor Gray
Write-Host "[*] Enter your MySQL root password if prompted:`n" -ForegroundColor Yellow

cmd /c "$mysqlBin -u root -p < database\schema.sql"

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n[OK] Database and tables created successfully!" -ForegroundColor Green
} else {
    Write-Host "`n[!] Could not connect to MySQL. Ensure MySQL service is running." -ForegroundColor Red
}

