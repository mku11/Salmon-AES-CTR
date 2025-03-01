@ECHO OFF
set CURRDIR=%CD%
set SALMON_VERSION=2.3.0

set JAVA_WS=java-ws
set WS_APP_PROPERTIES=.\config\application.properties
set WS_SCRIPT_SH=.\scripts\start-salmon-ws.sh
set WS_SCRIPT_BAT=.\scripts\start-salmon-ws.bat
set WS_WAR=..\build\libs\%JAVA_WS%-%SALMON_VERSION%.war

set PACKAGES_DIR=packages
set CONFIG_DIR=config
set OUTPUT_ROOT=..\..\..\..\output

set WS_OUTPUT_DIR=%OUTPUT_ROOT%\%JAVA_WS%
set WS_PACKAGE_NAME=%JAVA_WS%.%SALMON_VERSION%


powershell mkdir -ErrorAction SilentlyContinue %PACKAGES_DIR%
rd /s /q %PACKAGES_DIR%\%WS_PACKAGE_NAME%

:: Web Service

powershell mkdir -ErrorAction SilentlyContinue %PACKAGES_DIR%\%WS_PACKAGE_NAME%

powershell mkdir -ErrorAction SilentlyContinue %PACKAGES_DIR%\%WS_PACKAGE_NAME%\%CONFIG_DIR%
copy %WS_SCRIPT_BAT% %PACKAGES_DIR%\%WS_PACKAGE_NAME%
copy %WS_SCRIPT_SH% %PACKAGES_DIR%\%WS_PACKAGE_NAME%
copy %WS_WAR% %PACKAGES_DIR%\%WS_PACKAGE_NAME%
copy %WS_APP_PROPERTIES% %PACKAGES_DIR%\%WS_PACKAGE_NAME%\%CONFIG_DIR%
copy README.txt %PACKAGES_DIR%\%WS_PACKAGE_NAME%\README.txt
cd %PACKAGES_DIR%\%WS_PACKAGE_NAME%
powershell -command Compress-Archive -Force -DestinationPath ..\%WS_PACKAGE_NAME%.zip -Path *
cd ..\..\
powershell mkdir -ErrorAction SilentlyContinue %WS_OUTPUT_DIR%
copy %PACKAGES_DIR%\%WS_PACKAGE_NAME%.zip %WS_OUTPUT_DIR%

cd %CURRDIR%