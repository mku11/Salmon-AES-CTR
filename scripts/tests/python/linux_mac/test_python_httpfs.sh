CURRDIR=$(pwd)

# set env var HTTP_SERVER_URL to the HTTP server
export HTTP_SERVER_URL=http://localhost:8880
export TEST_DIR=/tmp/salmon/test
export TEST_MODE=Http
export ENABLE_GPU=false
export ENC_THREADS=1

cd ../../../../libs/test/salmon_fs_test_python

python3 -m unittest -v salmon_fs_http_tests.SalmonFSHttpTests

cd $CURRDIR