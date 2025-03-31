CURRDIR=$(pwd)
SALMON_VERSION=3.0.1
PLATFORM=linux
ARCH=x86_64
PACKAGE_NAME=salmon-gcc-$PLATFORM-$ARCH.$SALMON_VERSION
PACKAGE_FILE=$PACKAGE_NAME.tar.gz

export LD_LIBRARY_PATH=.:$LD_LIBRARY_PATH

cd ../deploy
./build_gcc_${PLATFORM}_${ARCH}.sh
cd ../../samples/GccSamples
rm -rf salmon-lib
tar -xzf ../../output/native/$PACKAGE_FILE
mv $PACKAGE_NAME salmon-lib
make
./salmon_sample

cd $CURRDIR