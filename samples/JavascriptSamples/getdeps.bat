set version=1.0.6-SNAPSHOT
rmdir packages /S /Q
mkdir packages
rmdir lib /S /Q
mkdir lib

set SALMON_CORE=salmon-core
set SALMON_FS=salmon-fs
set SALMON_CORE_LIB=%SALMON_CORE%.js.%version%
set SALMON_FS_LIB=%SALMON_FS%.js.%version%
set SALMON_CORE_LIB_FILENAME=%SALMON_CORE_LIB%.zip
set SALMON_FS_LIB_FILENAME=%SALMON_FS_LIB%.zip
set SALMON_LIBS_URL=http://localhost/repository/javascript
set SALMON_CORE_LIB_URL=%SALMON_LIBS_URL%/%SALMON_CORE_LIB_FILENAME%
set SALMON_FS_LIB_URL=%SALMON_LIBS_URL%/%SALMON_FS_LIB_FILENAME%

cd packages
curl %SALMON_CORE_LIB_URL% -o %SALMON_CORE_LIB_FILENAME%
curl %SALMON_FS_LIB_URL% -o %SALMON_FS_LIB_FILENAME%

powershell -command Expand-Archive -Force '%SALMON_CORE_LIB_FILENAME%'
powershell -command Expand-Archive -Force '%SALMON_FS_LIB_FILENAME%'

cd ..
move packages\%SALMON_CORE_LIB%\%SALMON_CORE% lib\
move packages\%SALMON_FS_LIB%\%SALMON_FS% lib\
