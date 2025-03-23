CURRDIR=$(pwd)

cd ../../libs/projects/salmon-libs-gcc
make package PLATFORM=linux ARCH=aarch64

cd $CURRDIR