@echo off
setlocal
cd /d "%~dp0"

echo ============================================
echo   Asset Report Dashboard - Docker
echo ============================================
echo.

echo [1/3] Kiem tra Docker Desktop...
docker info >nul 2>&1
if not errorlevel 1 goto dockerready
echo     Docker chua chay, dang khoi dong...
start "" "C:\Users\vuqua\AppData\Local\Programs\DockerDesktop\Docker Desktop.exe"
:waitdocker
timeout /t 4 /nobreak >nul
docker info >nul 2>&1
if errorlevel 1 goto waitdocker
:dockerready
echo     Docker san sang.

echo [2/3] Build va khoi dong 2 image (backend + frontend)...
docker compose -f docker-compose.report.yml up -d --build
if errorlevel 1 goto buildfail

echo [3/3] Doi backend san sang...
set /a n=0
:waitapi
set /a n+=1
timeout /t 2 /nobreak >nul
curl -s -o nul http://localhost:8091/api/report/health
if not errorlevel 1 goto apiready
if %n% lss 30 goto waitapi
:apiready

echo.
echo ============================================
echo   DANG CHAY
echo   Web:  http://localhost:8081/asset-overview
echo   API:  http://localhost:8091/api/report
echo ============================================
start "" http://localhost:8081/asset-overview
echo.
echo (Nhan phim bat ky de dong cua so nay - app van chay)
pause >nul
exit /b 0

:buildfail
echo.
echo [LOI] Build/khoi dong that bai. Xem log ben tren.
pause
exit /b 1
