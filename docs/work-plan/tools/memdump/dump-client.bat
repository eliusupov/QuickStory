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
echo NOTE: navigate to the world^-^>channel grid and leave it on screen; capture fires automatically when the channel screen is live.>> "%LOG%"
echo.
echo Navigate to the world-^>channel grid and leave it on screen.
echo Capture fires automatically when the channel screen is live (waiting up to 5 min)...
echo dumping MapleStory client when channel dialog is live...>> "%LOG%"
"%~dp0MemDump.exe" waitfull --name MapleStory --watch-va 0x00C4703C --watch-size 4 --timeout-sec 300 --out "%~dp0hdclient-full.bin" >> "%LOG%" 2>&1
echo exitcode=%errorlevel%>> "%LOG%"
if exist "%~dp0hdclient-full.bin" (
  echo SUCCESS - dump written>> "%LOG%"
) else (
  echo FAILED - no dump file>> "%LOG%"
)
echo.
echo Done. You can close this window.
timeout /t 5 >nul
