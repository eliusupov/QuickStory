@echo off
@title Cosmic
if defined JAVA_HOME (set "JAVA=%JAVA_HOME%\bin\java.exe") else (set "JAVA=java")
"%JAVA%" -Xmx2048m -Dwz-path=wz -jar target\Cosmic.jar
pause