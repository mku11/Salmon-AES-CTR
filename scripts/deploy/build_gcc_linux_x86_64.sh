CURRDIR=$(pwd)

cd ../../libs/projects/salmon-libs-gcc
make package PLATFORM=linux ARCH=x86_64 ENABLE_JNI=1 USE_OPENCL=1 OPENCL_INCLUDE=/usr/include OPENCL_LIB=/usr/lib/x86_64-linux-gnu

cd $CURRDIR