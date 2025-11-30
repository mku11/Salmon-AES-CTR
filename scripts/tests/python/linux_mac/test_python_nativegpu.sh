CURRDIR=$(pwd)

export ENABLE_GPU=true
export ENC_THREADS=1

cd ../../../../libs/test/salmon_native_test_python

export AES_PROVIDER_TYPE=AesGPU
python3 -m unittest -v salmon_native_tests.SalmonNativeTests

cd $CURRDIR