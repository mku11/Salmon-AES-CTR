CURRDIR=$(pwd)

# ALL
./test_csharp_core.sh
if [ $? -ne 0 ]; then exit 1; fi

./test_csharp_core_multi.sh
if [ $? -ne 0 ]; then exit 1; fi

./test_csharp_fs.sh
if [ $? -ne 0 ]; then exit 1; fi

./test_csharp_fs_multi.sh
if [ $? -ne 0 ]; then exit 1; fi

./test_csharp_httpfs.sh
if [ $? -ne 0 ]; then exit 1; fi

./test_csharp_httpfs_multi.sh
if [ $? -ne 0 ]; then exit 1; fi

./test_csharp_native.sh
if [ $? -ne 0 ]; then exit 1; fi

./test_csharp_native_multi.sh
if [ $? -ne 0 ]; then exit 1; fi

./test_csharp_wsfs.sh
if [ $? -ne 0 ]; then exit 1; fi

./test_csharp_wsfs_multi.sh
if [ $? -ne 0 ]; then exit 1; fi

cd $CURRDIR