set SALMON_VERSION=2.3.0
rmdir packages /S /Q
mkdir packages
rmdir assets\js\lib /S /Q
mkdir assets\js\lib

set SALMON_CORE=salmon-core
set SALMON_FS=salmon-fs
set SALMON_CORE_LIB=%SALMON_CORE%.js.%SALMON_VERSION%
set SALMON_FS_LIB=%SALMON_FS%.js.%SALMON_VERSION%
set SALMON_CORE_LIB_FILENAME=%SALMON_CORE_LIB%.zip
set SALMON_FS_LIB_FILENAME=%SALMON_FS_LIB%.zip

:: use local repo
set SALMON_LIBS_URL=http://localhost/repository/javascript
:: or official github release
:: set SALMON_LIBS_URL=https://github.com/mku11/Salmon-AES-CTR/releases/download/v%SALMON_VERSION%

set SALMON_CORE_LIB_URL=%SALMON_LIBS_URL%/%SALMON_CORE_LIB_FILENAME%
set SALMON_FS_LIB_URL=%SALMON_LIBS_URL%/%SALMON_FS_LIB_FILENAME%

cd packages
curl %SALMON_CORE_LIB_URL% -LJo %SALMON_CORE_LIB_FILENAME%
curl %SALMON_FS_LIB_URL% -LJo %SALMON_FS_LIB_FILENAME%

powershell -command Expand-Archive -Force '%SALMON_CORE_LIB_FILENAME%'
powershell -command Expand-Archive -Force '%SALMON_FS_LIB_FILENAME%'

cd ..
move packages\%SALMON_CORE_LIB%\%SALMON_CORE% assets\js\lib\
move packages\%SALMON_FS_LIB%\%SALMON_FS% assets\js\lib\
