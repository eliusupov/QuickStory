@echo off
@title Cosmic
rem GMS v84 is the only supported protocol; its opcode tables are bundled and loaded automatically.
java -Xmx2048m -Dwz-path=wz -jar target\Cosmic.jar
pause