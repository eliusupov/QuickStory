@echo off
if "%VCVARS32%"=="" set "VCVARS32=C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars32.bat"
call "%VCVARS32%" >nul 2>&1 || exit /b 1
cd /d "%~dp0"
cl /nologo /MT /O2 selftest.cpp /link /OUT:ijl-selftest.exe || exit /b 1
ijl-selftest.exe ijl15.fixed.dll
exit /b %errorlevel%
