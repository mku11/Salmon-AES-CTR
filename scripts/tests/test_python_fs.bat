set CURRDIR=%CD%

:: set TEST_DIR=c:\tmp\salmon\test

set TEST_MODE=Local

cd ..\..\libs\test\salmon_fs_test_python
pip install python-interface typeguard pycryptodome wrapt & ^
python -m unittest -v salmon_fs_tests.SalmonFSTests & ^
cd %CURRDIR%