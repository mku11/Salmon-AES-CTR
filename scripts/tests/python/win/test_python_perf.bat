set CURRDIR=%CD%

cd ..\..\..\..\libs\test\salmon_core_test_python

set ENABLE_GPU=false
set ENC_THREADS=1

call python -m unittest -v salmon_core_perf_tests.SalmonCorePerfTests
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

cd %CURRDIR%