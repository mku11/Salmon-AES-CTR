set CURRDIR=%CD%

cd ..\..\..\..\libs\test\salmon_core_test_python

set ENABLE_GPU=true
set ENC_THREADS=2

set AES_PROVIDER_TYPE=AesGPU
call python -m unittest -v salmon_core_tests.SalmonCoreTests
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

cd %CURRDIR%