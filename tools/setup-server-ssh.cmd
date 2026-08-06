@echo off
setlocal
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup-server-ssh.ps1" %*
exit /b %ERRORLEVEL%
