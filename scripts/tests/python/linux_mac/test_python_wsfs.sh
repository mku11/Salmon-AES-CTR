CURRDIR=$(pwd)

# set env var WS_SERVER_URL to the Web service URL
export WS_SERVER_URL=http://localhost:8080
export TEST_DIR=/tmp/salmon/test
export TEST_MODE=WebService
export ENABLE_GPU=false
export ENC_THREADS=1

cd ../../../../libs/test/salmon_fs_test_python

python3 -m unittest -v salmon_fs_tests.SalmonFSTests

cd $CURRDIR