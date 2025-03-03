@echo off
SET CURRDIR=%~dp0
java -cp %CURRDIR%salmon-ws.war org.springframework.boot.loader.WarLauncher