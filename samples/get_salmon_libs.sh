CURRDIR=$(pwd)

DEPS_DIR=./libs/
SALMON_LIB_VERSION=3.0.2-SNAPSHOT

SALMON_BINARY=salmon-multi-arch.v$SALMON_LIB_VERSION.zip
SALMON_URL=https://github.com/mku11/Salmon-AES-CTR/releases/download/v$SALMON_LIB_VERSION/$SALMON_BINARY
ZIP_FILENAME=salmon

mkdir -p $DEPS_DIR
curl $SALMON_URL -LJo $DEPS_DIR/$ZIP_FILENAME.zip
cd $DEPS_DIR
mkdir $ZIP_FILENAME
cd $ZIP_FILENAME
unzip -qq -o ../$ZIP_FILENAME.zip

# extract the native lib for windows
cd salmon-msvc-win-x86_64
cp -f Salmon.Native.3.0.2.nupkg Salmon.Native.3.0.2.zip
mkdir Salmon.Native.3.0.2
cd Salmon.Native.3.0.2
unzip -qq -o ../Salmon.Native.3.0.2.zip

cd $CURRDIR