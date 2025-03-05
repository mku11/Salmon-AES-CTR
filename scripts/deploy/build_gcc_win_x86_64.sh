CURRDIR=$(pwd)

cd ../../libs/projects/salmon-libs-gcc
make PLATFORM=win ARCH=x86_64 USE_OPENCL=1 ENABLE_JNI=1
make package PLATFORM=win ARCH=x86_64 USE_OPENCL=1 ENABLE_JNI=1

cd $CURRDIR