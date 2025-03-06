set CURRDIR=%CD%

cd ..\..\..\..\libs\test\salmon_core_test_python

set ENABLE_GPU=false
set ENC_THREADS=1

set AES_PROVIDER_TYPE=Default
call python -m unittest -v salmon_core_tests.SalmonCoreTests
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

set AES_PROVIDER_TYPE=Aes
call python -m unittest -v salmon_core_tests.SalmonCoreTests
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

set AES_PROVIDER_TYPE=AesIntrinsics
call python -m unittest -v salmon_core_tests.SalmonCoreTests
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

cd %CURRDIR%