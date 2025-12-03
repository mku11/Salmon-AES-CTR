CURRDIR=$(pwd)

cd ../../../../libs/test/salmon_core_test_python

export ENABLE_GPU=false
export ENC_THREADS=1

python -m unittest -v salmon_core_perf_tests.SalmonCorePerfTests

cd $CURRDIR