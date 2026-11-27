@echo off
cd /d "%~dp0"
if not exist out mkdir out
javac -cp "lib/sqlite-jdbc-3.53.0.0.jar" -d out src/com/shop/*.java src/com/shop/ui/*.java
if errorlevel 1 (
    echo.
    echo Compile failed. Read the error above.
    pause
    exit /b 1
)
start "" javaw -cp "out;lib/sqlite-jdbc-3.53.0.0.jar" com.shop.ui.App
