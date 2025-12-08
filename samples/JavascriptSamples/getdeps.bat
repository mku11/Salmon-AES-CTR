set CURRDIR=%CD%

set SIMPLE_IO_VERSION=1.0.2
set SIMPLE_FS_VERSION=1.0.2
set SALMON_VERSION=3.0.3

rmdir assets\js\lib /S /Q
mkdir assets\js\lib

set SALMON_DIR=..\libs\salmon\salmon-javascript
set SIMPLE_IO=simple-io
set SIMPLE_FS=simple-fs
set SALMON_CORE=salmon-core
set SALMON_FS=salmon-fs
set SIMPLE_IO_LIB=%SALMON_DIR%\%SIMPLE_IO%.js.%SIMPLE_IO_VERSION%
set SIMPLE_FS_LIB=%SALMON_DIR%\%SIMPLE_FS%.js.%SIMPLE_FS_VERSION%
set SALMON_CORE_LIB=%SALMON_DIR%\%SALMON_CORE%.js.%SALMON_VERSION%
set SALMON_FS_LIB=%SALMON_DIR%\%SALMON_FS%.js.%SALMON_VERSION%

robocopy /E %SIMPLE_IO_LIB% assets\js\lib\
robocopy /E %SIMPLE_FS_LIB% assets\js\lib\
robocopy /E %SALMON_CORE_LIB% assets\js\lib\
robocopy /E %SALMON_FS_LIB% assets\js\lib\

cd %CURRDIR%