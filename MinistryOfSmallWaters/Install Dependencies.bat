@echo off
rem Installs the two things the Ministry needs (Pillow + pystray).
rem tkinter already ships with the python.org installer.
cd /d "%~dp0"

where python >nul 2>nul
if not %errorlevel%==0 (
    echo Python was not found on your PATH.
    echo Install it from https://www.python.org/downloads/ ^(tick "Add to PATH"^) first.
    pause
    goto :eof
)

echo Installing dependencies for the Ministry of Small Waters...
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
echo.
echo Done. You may now double-click "Run Ministry.bat".
pause
