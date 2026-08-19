@echo off
@title Cosmic
rem GMS v84 is the only supported protocol; its opcode tables are bundled and loaded automatically.
rem Use JAVA_HOME (Corretto 21) - bare "java" on PATH may be Java 8, which cannot run this jar (class version 65.0).
if defined JAVA_HOME (set "JAVA=%JAVA_HOME%\bin\java.exe") else (set "JAVA=java")
"%JAVA%" -Xmx2048m -Dwz-path=wz -jar target\Cosmic.jar
pause
