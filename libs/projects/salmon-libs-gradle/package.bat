set SALMON_VERSION=3.0.2
set CURRDIR=%CD%

set OUTPUT_ROOT=%CURRDIR%\..\..\..\output
set NATIVE_OUTPUT_DIR=%OUTPUT_ROOT%\native
set SOURCE_ARCHIVE=.\salmon-native\build\libs\salmon-native.zip

mkdir %NATIVE_OUTPUT_DIR%
copy /Y %SOURCE_ARCHIVE% %NATIVE_OUTPUT_DIR%\salmon-gradle-win-x86_64.%SALMON_VERSION%.zip & ^

if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

cd %CURRDIR%
