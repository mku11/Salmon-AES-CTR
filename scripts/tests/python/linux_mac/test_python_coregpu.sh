CURRDIR=$(pwd)

export ENABLE_GPU=true
export ENC_THREADS=1
export AES_PROVIDER_TYPE=AesGPU

cd ../../../../libs/test/salmon_core_test_python

python3 -m unittest -v salmon_core_tests.SalmonCoreTests

cd $CURRDIR
