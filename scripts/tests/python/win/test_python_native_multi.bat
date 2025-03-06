set CURRDIR=%CD%

set ENABLE_GPU=false
set ENC_THREADS=2

cd ..\..\..\..\libs\test\salmon_native_test_python

set AES_PROVIDER_TYPE=Aes
call python -m unittest -v salmon_native_tests.SalmonNativeTests
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

set AES_PROVIDER_TYPE=AesIntrinsics
call python -m unittest -v salmon_native_tests.SalmonNativeTests
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

cd %CURRDIR%