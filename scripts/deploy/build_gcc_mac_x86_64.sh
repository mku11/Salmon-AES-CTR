CURRDIR=$(pwd)

cd ../../libs/projects/salmon-libs-gcc
make PLATFORM=mac ARCH=x86_64 ENABLE_JNI=1
make package PLATFORM=macos ARCH=x86_64 ENABLE_JNI=1

cd $CURRDIR