@echo off
title mcp-server-crawler
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run.ps1"
echo.
pause
