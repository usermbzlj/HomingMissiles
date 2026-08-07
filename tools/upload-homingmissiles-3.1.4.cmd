@echo off
setlocal
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0upload-homingmissiles-3.1.4.ps1" %*
exit /b %ERRORLEVEL%
