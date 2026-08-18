@echo off
setlocal
set "LOG=%~dp0dump-log.txt"
rem --- self-elevate if not admin ---
net session >nul 2>&1
if %errorlevel% neq 0 (
  echo [%date% %time%] not admin, requesting elevation> "%LOG%"
  powershell -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
  exit /b
)
echo [%date% %time%] running elevated> "%LOG%"
echo dumping MapleStory client (full address space)...>> "%LOG%"
"%~dp0MemDump.exe" full --name MapleStory --out "%~dp0hdclient-full.bin" >> "%LOG%" 2>&1
echo exitcode=%errorlevel%>> "%LOG%"
if exist "%~dp0hdclient-full.bin" (
  echo SUCCESS - dump written>> "%LOG%"
) else (
  echo FAILED - no dump file>> "%LOG%"
)
echo.
echo Done. You can close this window.
timeout /t 5 >nul
