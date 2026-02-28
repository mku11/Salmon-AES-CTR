CURRDIR=$(pwd)

./test_java_core.sh
if [ $? -ne 0 ]; then exit 1; fi

./test_java_core_multi.sh
if [ $? -ne 0 ]; then exit 1; fi

./test_java_fs.sh
if [ $? -ne 0 ]; then exit 1; fi

./test_java_fs_multi.sh
if [ $? -ne 0 ]; then exit 1; fi

./test_java_native.sh
if [ $? -ne 0 ]; then exit 1; fi

./test_java_native_multi.sh
if [ $? -ne 0 ]; then exit 1; fi

cd $CURRDIR