CURRDIR=$(pwd)

cd ../../libs/test/salmon_core_test_python
pip install python-interface typeguard pycryptodome wrapt
python3 -m unittest -v salmon_core_tests.SalmonCoreTests

cd $CURRDIR