#!/bin/bash -x
CURRDIR=$(pwd)

# ALL
./test_csharp_core.bat

./test_csharp_core_multi.bat

./test_csharp_fs.bat

./test_csharp_fs_multi.bat

./test_csharp_native.bat

./test_csharp_native_multi.bat

cd $CURRDIR