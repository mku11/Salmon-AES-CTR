CURRDIR=$(pwd)

# ALL
./test_java_core.sh
./test_java_core_multi.sh
./test_java_fs.sh
./test_java_fs_multi.sh
./test_java_httpfs.sh
./test_java_httpfs_multi.sh
./test_java_wsfs.sh
./test_java_wsfs_multi.sh

cd $CURRDIR