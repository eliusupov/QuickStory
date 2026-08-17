@echo off
REM Build hd-res as a 32-bit edits\ DLL, and run the load-and-run selftest.
REM
REM   build.cmd            build + selftest
REM   build.cmd nocheck    build only
REM
REM Requires MSVC. vcvars32.bat sets the x86 environment; a bare `cl` without it fails
REM on headers and libs. Override the path with VCVARS32 if yours differs.

if "%VCVARS32%"=="" set "VCVARS32=C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars32.bat"
if not exist "%VCVARS32%" (
  echo ERROR: vcvars32.bat not found at "%VCVARS32%"
  echo Set VCVARS32 to your Build Tools path.
  exit /b 1
)
call "%VCVARS32%" >nul 2>&1 || exit /b 1
cd /d "%~dp0"

REM Regenerate first if the tables are stale -- gen_loader.py writes hd_patches.inc and
REM hd_caves.inc from data/v84-patchset.json, which verify.py produces.
echo === building hd-res-3.2.0.dll (x86) ===
cl /nologo /LD /O2 /GS- /MT /DNDEBUG dllmain.cpp /link /OUT:hd-res-3.2.0.dll kernel32.lib user32.lib || exit /b 1

REM The only expected warning is C4733 from cc0x00A63FF3, a group-A cave that is NOT in
REM the shipped table and is never jumped to. Any other warning is worth reading.

if "%1"=="nocheck" goto :done

echo.
echo === selftest: does it load and run? ===
cl /nologo /LD /O2 /GS- /MT /DNDEBUG /DHD_SELFTEST dllmain.cpp /Fe:hd-res-selftest.dll /link kernel32.lib user32.lib || exit /b 1
cl /nologo /MT /DNDEBUG selftest.cpp /link /BASE:0x20000000 /OUT:hd-selftest.exe || exit /b 1
.\hd-selftest.exe hd-res-selftest.dll || exit /b 1
echo.
echo Expected above: "applied 0, skipped 0, byte-mismatch 292 of 292"
echo   and an archive line reading "off (no EzorsiaV2_UI.wz)".
echo That is the byte guard refusing to patch a process that is not the v84 client.
echo A NON-ZERO applied count here means the guard is broken -- do not ship it.

:done
echo.
dumpbin /nologo /headers hd-res-3.2.0.dll | findstr /C:"machine" /C:"File Type"
exit /b 0
