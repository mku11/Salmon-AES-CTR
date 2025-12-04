CURRDIR=$(pwd)

PLATFORM=macos
ARCH=aarch64

export LD_LIBRARY_PATH=.:$LD_LIBRARY_PATH

cd ../../samples/GccSamples
make clean
make PLATFORM=$PLATFORM ARCH=$ARCH
./salmon_sample

cd $CURRDIR