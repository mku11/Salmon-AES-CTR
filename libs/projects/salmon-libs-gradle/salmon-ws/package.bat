@ECHO OFF
set SALMON_VERSION=2.3.0

set CURRDIR=%CD%
set JAVA_WS=java-ws
set WS_APP_PROPERTIES=.\config\application.properties
set WS_SCRIPT_SH=.\scripts\start-salmon-ws.sh
set WS_SCRIPT_BAT=.\scripts\start-salmon-ws.bat
set WS_WAR=.\build\libs\salmon-ws-%SALMON_VERSION%.war
set WS_WAR_NAME=salmon-ws.war

set CONFIG_DIR=config
set OUTPUT_ROOT=..\..\..\..\output
set WS_OUTPUT_DIR=%OUTPUT_ROOT%\%JAVA_WS%
set WS_PACKAGE_NAME=%JAVA_WS%.%SALMON_VERSION%
set WS_PACKAGE_NAME_ZIP=%JAVA_WS%.%SALMON_VERSION%.zip

powershell mkdir -ErrorAction SilentlyContinue %WS_OUTPUT_DIR%

:: Web Service
rd /s /q %WS_OUTPUT_DIR%\%WS_PACKAGE_NAME%
powershell mkdir -ErrorAction SilentlyContinue %WS_OUTPUT_DIR%\%WS_PACKAGE_NAME%
powershell mkdir -ErrorAction SilentlyContinue %WS_OUTPUT_DIR%\%WS_PACKAGE_NAME%\%CONFIG_DIR%
copy %WS_SCRIPT_BAT% %WS_OUTPUT_DIR%\%WS_PACKAGE_NAME%
copy %WS_SCRIPT_SH% %WS_OUTPUT_DIR%\%WS_PACKAGE_NAME%
copy %WS_WAR% %WS_OUTPUT_DIR%\%WS_PACKAGE_NAME%\%WS_WAR_NAME%
copy %WS_APP_PROPERTIES% %WS_OUTPUT_DIR%\%WS_PACKAGE_NAME%\%CONFIG_DIR%
copy README.txt %WS_OUTPUT_DIR%\%WS_PACKAGE_NAME%\README.txt
cd %WS_OUTPUT_DIR%\%WS_PACKAGE_NAME%
powershell -command Compress-Archive -Force -DestinationPath ..\%WS_PACKAGE_NAME_ZIP% -Path *
cd %CURRDIR%