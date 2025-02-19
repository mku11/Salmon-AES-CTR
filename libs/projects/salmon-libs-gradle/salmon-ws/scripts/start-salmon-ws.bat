@echo off
SET CURRDIR=%~dp0
java -cp %CURRDIR%salmon-ws-2.2.0.war org.springframework.boot.loader.WarLauncher