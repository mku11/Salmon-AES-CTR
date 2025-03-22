CURRDIR=$(pwd)

export NODE_OPTIONS=--experimental-vm-modules

# ALL
./test_javascript_core.sh
./test_javascript_core_multi.sh
./test_javascript_fs.sh
./test_javascript_fs_multi.sh
./test_javascript_httpfs.sh
./test_javascript_httpfs_multi.sh
./test_javascript_wsfs.sh
./test_javascript_wsfs_multi.sh

cd $CURRDIR