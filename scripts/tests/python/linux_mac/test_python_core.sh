CURRDIR=$(pwd)

set ENABLE_GPU=false
set ENC_THREADS=1

cd ../../../../libs/test/salmon_core_test_python

python3 -m unittest -v salmon_core_tests.SalmonCoreTests

cd $CURRDIR