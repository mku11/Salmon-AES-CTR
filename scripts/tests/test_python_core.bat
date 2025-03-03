set CURRDIR=%CD%

cd ..\..\libs\test\salmon_core_test_python
pip install python-interface typeguard pycryptodome wrapt & ^
python -m unittest -v salmon_core_tests.SalmonCoreTests & ^
cd %CURRDIR%