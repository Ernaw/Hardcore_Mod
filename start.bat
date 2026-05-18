@echo off
REM ============================================================
REM  Demarrage du serveur Minecraft Hardcore Partage (Paper)
REM ============================================================
cd /d "%~dp0server"
set JAVA="%~dp0jdk\bin\java.exe"
%JAVA% -Xms2G -Xmx4G -XX:+UseG1GC -jar paper.jar --nogui
pause
