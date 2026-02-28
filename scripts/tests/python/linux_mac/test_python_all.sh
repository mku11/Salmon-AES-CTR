CURRDIR=(pwd)

# ALL
./test_python_core.sh
if [ $? -ne 0 ]; then exit 1; fi

./test_python_core_multi.sh
if [ $? -ne 0 ]; then exit 1; fi

./test_python_fs.sh
if [ $? -ne 0 ]; then exit 1; fi

./test_python_fs_multi.sh
if [ $? -ne 0 ]; then exit 1; fi

./test_python_httpfs.sh
if [ $? -ne 0 ]; then exit 1; fi

./test_python_httpfs_multi.sh
if [ $? -ne 0 ]; then exit 1; fi

./test_python_native.sh
if [ $? -ne 0 ]; then exit 1; fi

./test_python_native_multi.sh
if [ $? -ne 0 ]; then exit 1; fi

./test_python_wsfs.sh
if [ $? -ne 0 ]; then exit 1; fi

./test_python_wsfs_multi.sh
if [ $? -ne 0 ]; then exit 1; fi

cd $CURRDIR