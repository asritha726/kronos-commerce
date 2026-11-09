@echo off
setlocal
echo =========================================================
echo    Apex OMS - Order ^& Inventory Management System
echo =========================================================

set "JAVAC_BIN=C:\Program Files\Java\jdk-26.0.2\bin\javac.exe"
set "JAVA_BIN=C:\Program Files\Java\jdk-26.0.2\bin\java.exe"

if not exist "%JAVAC_BIN%" (
    set "JAVAC_BIN=javac"
    set "JAVA_BIN=java"
)

if not exist "backend\bin" (
    mkdir "backend\bin"
)

echo [*] Compiling Java files...
"%JAVAC_BIN%" -d backend\bin @sources.txt
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Compilation failed!
    pause
    exit /b %ERRORLEVEL%
)

echo [OK] Compiled successfully.
echo [*] Starting Apex OMS on http://localhost:8080 ...
echo [*] Open your browser at http://localhost:8080
echo [*] Press Ctrl+C to stop.
echo.

"%JAVA_BIN%" -cp backend\bin com.fintech.oms.Main

