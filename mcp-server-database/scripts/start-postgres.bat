@echo off
title mcp-server-database [postgres]
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0_run.ps1" -DbType postgres
echo.
pause
