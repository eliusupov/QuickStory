@echo off
@title Cosmic
rem Opcode table: add -Dopcode-version=84 to load resources/opcodes/{send,recv}ops-84.properties (default 83)
java -Xmx2048m -Dwz-path=wz -jar target\Cosmic.jar
pause