#!/bin/bash -x
CURRDIR=$(pwd)

# ALL
./test_csharp_nativegpu.sh

./test_csharp_nativegpu_multi.sh

./test_csharp_coregpu.sh

./test_csharp_coregpu_multi.sh

cd %CURRDIR%