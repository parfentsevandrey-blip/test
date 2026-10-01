@echo off
rem Home Server control panel: double-click this file.
rem It starts home-server.ps1, which asks for administrator rights and opens the panel window.
chcp 65001 >nul
if not exist "%~dp0home-server.ps1" (
  echo.
  echo   Сначала распакуйте архив целиком: правой кнопкой по ZIP-файлу -^> «Извлечь все»,
  echo   затем откройте папку deploy\windows и запустите home-server.cmd оттуда.
  echo.
  pause
  exit /b 1
)
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0home-server.ps1"
if errorlevel 1 pause
