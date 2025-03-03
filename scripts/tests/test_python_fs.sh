CURRDIR=$(pwd)

# export TEST_DIR=/tmp/salmon/test

export TEST_MODE=Local

cd ../../libs/test/salmon_fs_test_python
pip install python-interface typeguard pycryptodome wrapt
python3 -m unittest -v salmon_fs_tests.SalmonFSTests

cd $CURRDIR