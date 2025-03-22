CURRDIR=$(pwd)

cd ../../libs/projects/salmon-libs-gcc
make PLATFORM=win ARCH=x86_64 USE_OPENCL=1 ENABLE_JNI=1
make package PLATFORM=win ARCH=x86_64 USE_OPENCL=1 ENABLE_JNI=1 OPENCL_INCLUDE=/cygdrive/d/tools/OpenCL-SDK-v2024.05.08-Win-x64/include OPENCL_LIB=/cygdrive/d/tools/OpenCL-SDK-v2024.05.08-Win-x64/lib

cd $CURRDIR