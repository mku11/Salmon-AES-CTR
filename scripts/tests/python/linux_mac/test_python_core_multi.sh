CURRDIR=$(pwd)

export ENABLE_GPU=false
export ENC_THREADS=2

cd ../../../../libs/test/salmon_core_test_python

python3 -m unittest -v salmon_core_tests.SalmonCoreTests

cd $CURRDIR