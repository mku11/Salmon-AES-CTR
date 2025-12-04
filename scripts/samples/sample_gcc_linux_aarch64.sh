CURRDIR=$(pwd)

PLATFORM=linux
ARCH=aarch64

export LD_LIBRARY_PATH=.:$LD_LIBRARY_PATH

cd ../../samples/GccSamples
make PLATFORM=$PLATFORM ARCH=$ARCH
./salmon_sample

cd $CURRDIR