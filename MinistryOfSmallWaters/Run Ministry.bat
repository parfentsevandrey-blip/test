@echo off
rem ============================================================
rem  Ministry of Small Waters — launcher
rem  Opens the tank with no console window if pythonw is available,
rem  otherwise falls back to python (a console tags along).
rem ============================================================
cd /d "%~dp0"

where pythonw >nul 2>nul
if %errorlevel%==0 (
    start "" pythonw "%~dp0main.py"
    goto :eof
)

where python >nul 2>nul
if %errorlevel%==0 (
    python "%~dp0main.py"
    goto :eof
)

echo Python was not found on your PATH.
echo Install it from https://www.python.org/downloads/ (tick "Add to PATH"),
echo then run  Install Dependencies.bat  once, and try again.
pause
