@ECHO ON
set CURRDIR=%CD%

set DEPS_DIR=.\libs\
set SALMON_VERSION=3.0.3

set SALMON_BINARY=salmon-multi-arch.v%SALMON_VERSION%.zip
set SALMON_URL=https://github.com/mku11/Salmon-AES-CTR/releases/download/v%SALMON_VERSION%/%SALMON_BINARY%
set ZIP_FILENAME=salmon

powershell mkdir -ErrorAction SilentlyContinue %DEPS_DIR%
curl %SALMON_URL% -LJo %DEPS_DIR%\%ZIP_FILENAME%.zip
cd %DEPS_DIR%
powershell -command Expand-Archive -Force %ZIP_FILENAME%.zip

:: extract the native lib for windows
cd %ZIP_FILENAME%\salmon-msvc-win-x86_64
copy /Y Salmon.Native.%SALMON_VERSION%.nupkg Salmon.Native.%SALMON_VERSION%.zip
powershell -command Expand-Archive -Force Salmon.Native.%SALMON_VERSION%.zip

cd %CURRDIR%