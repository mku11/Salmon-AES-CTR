:: @ECHO OFF
set SALMON_VERSION=2.3.0
set TS_SRC_ROOT=..\..\src\typescript
set JS_SRC_ROOT=.\lib
set OUTPUT_ROOT=..\..\..\output
set TS_OUTPUT_DIR=%OUTPUT_ROOT%\typescript
set JS_OUTPUT_DIR=%OUTPUT_ROOT%\javascript
set DOCS=.\docs

set PACKAGES_DIR=packages
set SALMON_CORE=salmon-core
set SALMON_FS=salmon-fs
set TS_SALMON_CORE_PACKAGE_NAME=%SALMON_CORE%.ts.%SALMON_VERSION%
set TS_SALMON_FS_PACKAGE_NAME=%SALMON_FS%.ts.%SALMON_VERSION%
set JS_SALMON_CORE_PACKAGE_NAME=%SALMON_CORE%.js.%SALMON_VERSION%
set JS_SALMON_FS_PACKAGE_NAME=%SALMON_FS%.js.%SALMON_VERSION%

powershell mkdir -ErrorAction SilentlyContinue %PACKAGES_DIR%
rd /s /q %PACKAGES_DIR%\%TS_SALMON_CORE_PACKAGE_NAME%
rd /s /q %PACKAGES_DIR%\%TS_SALMON_FS_PACKAGE_NAME%
rd /s /q %PACKAGES_DIR%\%JS_SALMON_CORE_PACKAGE_NAME%
rd /s /q %PACKAGES_DIR%\%JS_SALMON_FS_PACKAGE_NAME%

:: TS salmon-core
powershell mkdir %PACKAGES_DIR%\%TS_SALMON_CORE_PACKAGE_NAME%
xcopy /E %TS_SRC_ROOT%\%SALMON_CORE% %PACKAGES_DIR%\%TS_SALMON_CORE_PACKAGE_NAME%
copy %DOCS%\salmon.Core.README.txt %PACKAGES_DIR%\%TS_SALMON_CORE_PACKAGE_NAME%\README.txt
cd %PACKAGES_DIR%\%TS_SALMON_CORE_PACKAGE_NAME%
powershell -command Compress-Archive -Force -DestinationPath ..\%TS_SALMON_CORE_PACKAGE_NAME%.zip -Path *
cd ..\..\
powershell mkdir -ErrorAction SilentlyContinue %TS_OUTPUT_DIR%
copy /Y %PACKAGES_DIR%\%TS_SALMON_CORE_PACKAGE_NAME%.zip %TS_OUTPUT_DIR%

:: TS salmon-fs
powershell mkdir %PACKAGES_DIR%\%TS_SALMON_FS_PACKAGE_NAME%
xcopy /E %TS_SRC_ROOT%\%SALMON_CORE% %PACKAGES_DIR%\%TS_SALMON_FS_PACKAGE_NAME%
copy %DOCS%\salmon.Core.README.txt %PACKAGES_DIR%\%TS_SALMON_FS_PACKAGE_NAME%\README.txt
cd %PACKAGES_DIR%\%TS_SALMON_FS_PACKAGE_NAME%
powershell -command Compress-Archive -Force -DestinationPath ..\%TS_SALMON_FS_PACKAGE_NAME%.zip -Path *
cd ..\..\
powershell mkdir -ErrorAction SilentlyContinue %TS_OUTPUT_DIR%
copy /Y %PACKAGES_DIR%\%TS_SALMON_FS_PACKAGE_NAME%.zip %TS_OUTPUT_DIR%

:: JS salmon-core
powershell mkdir %PACKAGES_DIR%\%JS_SALMON_CORE_PACKAGE_NAME%
xcopy /E %JS_SRC_ROOT%\%SALMON_CORE% %PACKAGES_DIR%\%JS_SALMON_CORE_PACKAGE_NAME%
copy %DOCS%\salmon.Core.README.txt %PACKAGES_DIR%\%JS_SALMON_CORE_PACKAGE_NAME%\README.txt
cd %PACKAGES_DIR%\%JS_SALMON_CORE_PACKAGE_NAME%
powershell -command Compress-Archive -Force -DestinationPath ..\%JS_SALMON_CORE_PACKAGE_NAME%.zip -Path *
cd ..\..\
powershell mkdir -ErrorAction SilentlyContinue %JS_OUTPUT_DIR%
copy /Y %PACKAGES_DIR%\%JS_SALMON_CORE_PACKAGE_NAME%.zip %JS_OUTPUT_DIR%

:: JS salmon-fs
powershell mkdir %PACKAGES_DIR%\%JS_SALMON_FS_PACKAGE_NAME%
xcopy /E %JS_SRC_ROOT%\%SALMON_CORE% %PACKAGES_DIR%\%JS_SALMON_FS_PACKAGE_NAME%
copy %DOCS%\salmon.Core.README.txt %PACKAGES_DIR%\%JS_SALMON_FS_PACKAGE_NAME%\README.txt
cd %PACKAGES_DIR%\%JS_SALMON_FS_PACKAGE_NAME%
powershell -command Compress-Archive -Force -DestinationPath ..\%JS_SALMON_FS_PACKAGE_NAME%.zip -Path *
cd ..\..\
powershell mkdir -ErrorAction SilentlyContinue %JS_OUTPUT_DIR%
copy /Y %PACKAGES_DIR%\%JS_SALMON_FS_PACKAGE_NAME%.zip %JS_OUTPUT_DIR%