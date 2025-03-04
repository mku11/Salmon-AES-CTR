set CURRDIR=%CD%

:: set env var WS_SERVER_URL to the Web service URL
set WS_SERVER_URL=http://localhost:8080
set TEST_DIR=d:\tmp\salmon\test
set TEST_MODE=WebService

cd ..\..\libs\test\salmon_fs_test_python
pip install python-interface typeguard pycryptodome wrapt & ^
python -m unittest -v salmon_fs_tests.SalmonFSTests & ^
cd %CURRDIR%