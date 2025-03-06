set CURRDIR=%CD%

set ENABLE_GPU=true
set ENC_THREADS=1

cd ..\..\..\..\libs\test\salmon_native_test_python

set AES_PROVIDER_TYPE=AesGPU
call python -m unittest -v salmon_native_tests.SalmonNativeTests
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

cd %CURRDIR%