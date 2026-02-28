CURRDIR=$(pwd)

export ENABLE_GPU=true
export ENC_THREADS=2
export AES_PROVIDER_TYPE=AesGPU

cd ../../../../libs/test/salmon_core_test_python

python3 -m unittest -v salmon_core_tests.SalmonCoreTests
if [ $? -ne 0 ]; then exit 1; fi

cd $CURRDIR
