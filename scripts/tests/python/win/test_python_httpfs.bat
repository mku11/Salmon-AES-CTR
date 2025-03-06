set CURRDIR=%CD%

:: set env var HTTP_SERVER_URL to the HTTP server
set HTTP_SERVER_URL=http://localhost:8000
set TEST_DIR=d:\tmp\salmon\test
set TEST_MODE=Http
set ENC_THREADS=1
set ENABLE_GPU=false
set AES_PROVIDER_TYPE=Default

cd ..\..\..\..\libs\test\salmon_fs_test_python

python -m unittest -v salmon_fs_http_tests.SalmonFSHttpTests
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

cd %CURRDIR%