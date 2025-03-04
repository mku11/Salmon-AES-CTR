set CURRDIR=%CD%

:: set env var HTTP_SERVER_URL to the HTTP server
:: set HTTP_SERVER_URL=http://localhost
:: set TEST_DIR=c:\tmp\salmon\test

set TEST_MODE=Http

cd ..\..\libs\test\salmon_fs_test_python
pip install python-interface typeguard pycryptodome wrapt & ^
python -m unittest -v salmon_fs_http_tests.SalmonFSHttpTests & ^
cd %CURRDIR%