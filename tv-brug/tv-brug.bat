@echo off
title Glass Home tv-brug
echo Glass Home tv-brug wordt gestart. Laat dit venster open tot de tv verbonden is.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tv-brug.ps1" -Setup
pause
