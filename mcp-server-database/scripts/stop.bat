@echo off
REM 结束占用 8102 的进程（mcp-server-database）
setlocal
set PORT=8102
for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":%PORT% " ^| findstr LISTENING') do (
  echo Killing PID %%p on port %PORT%
  taskkill /F /PID %%p >nul 2>&1
)
echo Done. If the console window is still open, close it manually.
pause
