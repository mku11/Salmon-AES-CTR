:: @ECHO OFF
set SALMON_VERSION=3.0.1
set CURRDIR=%CD%

set TS_SRC_ROOT=..\..\src\typescript
set JS_SRC_ROOT=.\lib
set OUTPUT_ROOT=%CURRDIR%\..\..\..\output
set TS_OUTPUT_DIR=%OUTPUT_ROOT%\typescript
set JS_OUTPUT_DIR=%OUTPUT_ROOT%\javascript
set DOCS=.\docs

set SALMON_CORE=salmon-core
set SALMON_FS=salmon-fs
set TS_SALMON_CORE_PACKAGE_NAME=%SALMON_CORE%.ts.%SALMON_VERSION%
set TS_SALMON_FS_PACKAGE_NAME=%SALMON_FS%.ts.%SALMON_VERSION%
set JS_SALMON_CORE_PACKAGE_NAME=%SALMON_CORE%.js.%SALMON_VERSION%
set JS_SALMON_FS_PACKAGE_NAME=%SALMON_FS%.js.%SALMON_VERSION%

:: Until ms fixes compress archive
echo Cannot use zip file in linux that have been created with Compress-Archive backslashes, use package.sh instead
exit /b

powershell mkdir -ErrorAction SilentlyContinue %TS_OUTPUT_DIR%
powershell mkdir -ErrorAction SilentlyContinue %JS_OUTPUT_DIR%

:: Typescript SalmonCore
rd /s /q %TS_OUTPUT_DIR%\%TS_SALMON_CORE_PACKAGE_NAME%
powershell mkdir %TS_OUTPUT_DIR%\%TS_SALMON_CORE_PACKAGE_NAME%
powershell mkdir %TS_OUTPUT_DIR%\%TS_SALMON_CORE_PACKAGE_NAME%\%SALMON_CORE%
xcopy /E %TS_SRC_ROOT%\%SALMON_CORE% %TS_OUTPUT_DIR%\%TS_SALMON_CORE_PACKAGE_NAME%\%SALMON_CORE%
copy %DOCS%\salmon.Core.README.txt %TS_OUTPUT_DIR%\%TS_SALMON_CORE_PACKAGE_NAME%\README.txt
cd %TS_OUTPUT_DIR%\%TS_SALMON_CORE_PACKAGE_NAME%
powershell -command Compress-Archive -Force -DestinationPath ..\%TS_SALMON_CORE_PACKAGE_NAME%.zip -Path *
cd %CURRDIR%

:: Typescript SalmonFS
rd /s /q %TS_OUTPUT_DIR%\%TS_SALMON_FS_PACKAGE_NAME%
powershell mkdir %TS_OUTPUT_DIR%\%TS_SALMON_FS_PACKAGE_NAME%
powershell mkdir %TS_OUTPUT_DIR%\%TS_SALMON_FS_PACKAGE_NAME%\%SALMON_FS%
xcopy /E %TS_SRC_ROOT%\%SALMON_FS% %TS_OUTPUT_DIR%\%TS_SALMON_FS_PACKAGE_NAME%\%SALMON_FS%
copy %DOCS%\salmon.FS.README.txt %TS_OUTPUT_DIR%\%TS_SALMON_FS_PACKAGE_NAME%\README.txt
cd %TS_OUTPUT_DIR%\%TS_SALMON_FS_PACKAGE_NAME%
powershell -command Compress-Archive -Force -DestinationPath ..\%TS_SALMON_FS_PACKAGE_NAME%.zip -Path *
cd %CURRDIR%

:: Javascript SalmonCore
rd /s /q %JS_OUTPUT_DIR%\%JS_SALMON_CORE_PACKAGE_NAME%
powershell mkdir %JS_OUTPUT_DIR%\%JS_SALMON_CORE_PACKAGE_NAME%
powershell mkdir %JS_OUTPUT_DIR%\%JS_SALMON_CORE_PACKAGE_NAME%\%SALMON_CORE%
xcopy /E %JS_SRC_ROOT%\%SALMON_CORE% %JS_OUTPUT_DIR%\%JS_SALMON_CORE_PACKAGE_NAME%\%SALMON_CORE%
copy %DOCS%\salmon.Core.README.txt %JS_OUTPUT_DIR%\%JS_SALMON_CORE_PACKAGE_NAME%\README.txt
cd %JS_OUTPUT_DIR%\%JS_SALMON_CORE_PACKAGE_NAME%
powershell -command Compress-Archive -Force -DestinationPath ..\%JS_SALMON_CORE_PACKAGE_NAME%.zip -Path *
cd %CURRDIR%

:: Javascript SalmonFS
rd /s /q %JS_OUTPUT_DIR%\%JS_SALMON_FS_PACKAGE_NAME%
powershell mkdir %JS_OUTPUT_DIR%\%JS_SALMON_FS_PACKAGE_NAME%
powershell mkdir %JS_OUTPUT_DIR%\%JS_SALMON_FS_PACKAGE_NAME%\%SALMON_FS%
xcopy /E %JS_SRC_ROOT%\%SALMON_FS% %JS_OUTPUT_DIR%\%JS_SALMON_FS_PACKAGE_NAME%\%SALMON_FS%
copy %DOCS%\salmon.FS.README.txt %JS_OUTPUT_DIR%\%JS_SALMON_FS_PACKAGE_NAME%\README.txt
cd %JS_OUTPUT_DIR%\%JS_SALMON_FS_PACKAGE_NAME%
powershell -command Compress-Archive -Force -DestinationPath ..\%JS_SALMON_FS_PACKAGE_NAME%.zip -Path *
cd %CURRDIR%