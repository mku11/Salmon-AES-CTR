CURRDIR=$(pwd)

# ALL
./test_java_core.sh
if [ $? -ne 0 ]; then exit 1; fi

./test_java_core_multi.sh
if [ $? -ne 0 ]; then exit 1; fi

./test_java_fs.sh
if [ $? -ne 0 ]; then exit 1; fi

./test_java_fs_multi.sh
if [ $? -ne 0 ]; then exit 1; fi

./test_java_httpfs.sh
if [ $? -ne 0 ]; then exit 1; fi

./test_java_httpfs_multi.sh
if [ $? -ne 0 ]; then exit 1; fi

./test_java_wsfs.sh
if [ $? -ne 0 ]; then exit 1; fi

./test_java_wsfs_multi.sh
if [ $? -ne 0 ]; then exit 1; fi

cd $CURRDIR