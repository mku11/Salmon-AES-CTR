CURRDIR=$(pwd)

# ALL
./test_csharp_nativegpu.sh
if [ $? -ne 0 ]; then exit 1; fi

./test_csharp_nativegpu_multi.sh
if [ $? -ne 0 ]; then exit 1; fi

./test_csharp_coregpu.sh
if [ $? -ne 0 ]; then exit 1; fi

./test_csharp_coregpu_multi.sh
if [ $? -ne 0 ]; then exit 1; fi

cd %CURRDIR%