@echo off
echo =========================================================
echo    Apex OMS - MySQL Database Setup
echo =========================================================

set "MYSQL_BIN=C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"

if not exist "%MYSQL_BIN%" (
    set "MYSQL_BIN=mysql"
)

echo [*] Importing database\schema.sql into MySQL...
echo [*] Enter your MySQL root password if prompted:
echo.

"%MYSQL_BIN%" -u root -p < database\schema.sql

if %ERRORLEVEL% EQU 0 (
    echo.
    echo [OK] Database and tables created successfully!
) else (
    echo.
    echo [!] Could not connect to MySQL. Ensure MySQL service is running.
)

pause

