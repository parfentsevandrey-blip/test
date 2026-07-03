@echo off
rem ============================================================
rem  Build a single self-contained Windows .exe with PyInstaller.
rem  Output: dist\MinistryOfSmallWaters.exe  (no Python needed to run it)
rem ============================================================
cd /d "%~dp0"

where python >nul 2>nul
if not %errorlevel%==0 (
    echo Python was not found on your PATH. Install it first.
    pause
    goto :eof
)

echo Installing build tools...
python -m pip install --upgrade pyinstaller Pillow pystray

echo Refreshing the crowned-crab icon...
python seal.py

echo Building the executable (this takes a minute)...
pyinstaller --noconfirm --onefile --windowed ^
    --name "MinistryOfSmallWaters" ^
    --icon "assets\icon.ico" ^
    main.py

echo.
echo Done. Your standalone Ministry is at:  dist\MinistryOfSmallWaters.exe
pause
