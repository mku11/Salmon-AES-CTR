CURRDIR=$(pwd)

cd ../../libs/projects/salmon-libs-gcc
make package PLATFORM=linux ARCH=aarch64 USE_OPENCL=1 ENABLE_JNI=1 OPENCL_INCLUDE=/usr/include OPENCL_LIB=/usr/lib/aarch64-linux-gnu

cd $CURRDIR