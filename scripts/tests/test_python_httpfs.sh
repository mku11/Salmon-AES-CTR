CURRDIR=$(pwd)

# set env var HTTP_SERVER_URL to the HTTP server
# export HTTP_SERVER_URL=http://localhost
# export TEST_DIR=/tmp/salmon/test

export TEST_MODE=Http

cd ../../libs/test/salmon_fs_test_python
pip install python-interface typeguard pycryptodome wrapt
python3 -m unittest -v salmon_fs_http_tests.SalmonFSHttpTests

cd $CURRDIR