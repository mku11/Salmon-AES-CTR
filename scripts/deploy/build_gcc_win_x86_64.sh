CURRDIR=$(pwd)

cd ../../libs/projects/salmon-libs-gcc
make package PLATFORM=win ARCH=x86_64 USE_OPENCL=1 ENABLE_JNI=1 OPENCL_INCLUDE=../../deps/OpenCL/OpenCL-SDK-v2025.07.23-Win-x64/include OPENCL_LIB=../../deps/OpenCL/OpenCL-SDK-v2025.07.23-Win-x64/lib

cd $CURRDIR