 @echo off
 SET CURRDIR=%~dp0
@REM  SET LOGGING=-Dorg.apache.commons.logging.Log=org.apache.commons.logging.impl.NoOpLog

 if exist "%1" (
   java -cp %CURRDIR%salmon-ws-2.1.0.war org.springframework.boot.loader.WarLauncher %1
 ) else (
   echo You need to pass an argument for the location of the drive
   echo ie: start-salmon-ws.bat "D:\\pathto\\mydrive"
   set /p key="Press any key to exit"
 )

