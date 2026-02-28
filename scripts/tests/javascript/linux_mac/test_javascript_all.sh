CURRDIR=$(pwd)

export NODE_OPTIONS=--experimental-vm-modules

# ALL
./test_javascript_core.sh
if [ $? -ne 0 ]; then exit 1; fi

./test_javascript_core_multi.sh
if [ $? -ne 0 ]; then exit 1; fi

./test_javascript_fs.sh
if [ $? -ne 0 ]; then exit 1; fi

./test_javascript_fs_multi.sh
if [ $? -ne 0 ]; then exit 1; fi

./test_javascript_httpfs.sh
if [ $? -ne 0 ]; then exit 1; fi

./test_javascript_httpfs_multi.sh
if [ $? -ne 0 ]; then exit 1; fi

./test_javascript_native.sh
if [ $? -ne 0 ]; then exit 1; fi

./test_javascript_native_multi.sh
if [ $? -ne 0 ]; then exit 1; fi

./test_javascript_wsfs.sh
if [ $? -ne 0 ]; then exit 1; fi

./test_javascript_wsfs_multi.sh
if [ $? -ne 0 ]; then exit 1; fi

cd $CURRDIR