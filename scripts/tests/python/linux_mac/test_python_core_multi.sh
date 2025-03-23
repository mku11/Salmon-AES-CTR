CURRDIR=$(pwd)

export ENABLE_GPU=false
export ENC_THREADS=2

cd ../../../../libs/test/salmon_core_test_python

export AES_PROVIDER_TYPE=Default
python3 -m unittest -v salmon_core_tests.SalmonCoreTests

export AES_PROVIDER_TYPE=Aes
python3 -m unittest -v salmon_core_tests.SalmonCoreTests

export AES_PROVIDER_TYPE=AesIntrinsics
python3 -m unittest -v salmon_core_tests.SalmonCoreTests

cd $CURRDIR