set CURRDIR=%CD%

set TEST_DIR=d:\tmp\salmon\test
set TEST_MODE=Local
set ENC_THREADS=1
set ENABLE_GPU=false
set AES_PROVIDER_TYPE=Default

cd ..\..\..\..\libs\test\salmon_fs_test_python

call python -m unittest -v salmon_fs_tests.SalmonFSTests
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

cd %CURRDIR%