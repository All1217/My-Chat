@echo off
title mcp-server-database [mysql]
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0_run.ps1" -DbType mysql
echo.
pause
