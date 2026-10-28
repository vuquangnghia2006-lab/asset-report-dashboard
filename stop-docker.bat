@echo off
setlocal
cd /d "%~dp0"

echo Dang dung 2 container (backend + frontend)...
docker compose -f docker-compose.report.yml down

echo.
echo Da dung. Nhan phim bat ky de dong.
pause >nul
