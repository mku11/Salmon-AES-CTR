set DEPS_DIR=.\libs\
set SALMON_LIB_VERSION=3.0.2-SNAPSHOT

set SALMON_BINARY=salmon-multi-arch.v%SALMON_LIB_VERSION%.zip
set SALMON_URL=https://github.com/mku11/Salmon-AES-CTR/releases/download/v%SALMON_LIB_VERSION%/%SALMON_BINARY%
set ZIP_FILENAME=salmon.zip

powershell mkdir -ErrorAction SilentlyContinue %DEPS_DIR%
curl %SALMON_URL% -LJo %DEPS_DIR%\%ZIP_FILENAME%
cd %DEPS_DIR%
powershell -command Expand-Archive -Force %ZIP_FILENAME%

