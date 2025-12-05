@ECHO ON
set CURRDIR=%CD%

set DEPS_DIR=.\libs\
set SALMON_LIB_VERSION=3.0.2

set SALMON_BINARY=salmon-multi-arch.v%SALMON_LIB_VERSION%.zip
set SALMON_URL=https://github.com/mku11/Salmon-AES-CTR/releases/download/v%SALMON_LIB_VERSION%/%SALMON_BINARY%
set ZIP_FILENAME=salmon

powershell mkdir -ErrorAction SilentlyContinue %DEPS_DIR%
curl %SALMON_URL% -LJo %DEPS_DIR%\%ZIP_FILENAME%.zip
cd %DEPS_DIR%
powershell -command Expand-Archive -Force %ZIP_FILENAME%.zip

:: extract the native lib for windows
cd %ZIP_FILENAME%\salmon-msvc-win-x86_64
copy /Y Salmon.Native.3.0.2.nupkg Salmon.Native.3.0.2.zip
powershell -command Expand-Archive -Force Salmon.Native.3.0.2.zip

cd %CURRDIR%