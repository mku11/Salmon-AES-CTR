CURRDIR=$(pwd)

export ENABLE_GPU=false
export ENC_THREADS=1

cd ../../../../libs/test/salmon_core_test_python

export AES_PROVIDER_TYPE=Default
python3 -m unittest -v salmon_core_tests.SalmonCoreTests
if [ $? -ne 0 ]; then exit 1; fi

export AES_PROVIDER_TYPE=Aes
python3 -m unittest -v salmon_core_tests.SalmonCoreTests
if [ $? -ne 0 ]; then exit 1; fi

export AES_PROVIDER_TYPE=AesIntrinsics
python3 -m unittest -v salmon_core_tests.SalmonCoreTests
if [ $? -ne 0 ]; then exit 1; fi

cd $CURRDIR