CURRDIR=$(pwd)

DEPS_DIR=./libs/
SALMON_VERSION=3.0.3

SALMON_BINARY=salmon-multi-arch.v$SALMON_VERSION.zip
SALMON_URL=https://github.com/mku11/Salmon-AES-CTR/releases/download/v$SALMON_VERSION/$SALMON_BINARY
ZIP_FILENAME=salmon

mkdir -p $DEPS_DIR
curl $SALMON_URL -LJo $DEPS_DIR/$ZIP_FILENAME.zip
cd $DEPS_DIR
mkdir $ZIP_FILENAME
cd $ZIP_FILENAME
unzip -qq -o ../$ZIP_FILENAME.zip

# extract the native lib for windows
cd salmon-msvc-win-x86_64
cp -f Salmon.Native.$SALMON_VERSION.nupkg Salmon.Native.$SALMON_VERSION.zip
mkdir Salmon.Native.$SALMON_VERSION
cd Salmon.Native.$SALMON_VERSION
unzip -qq -o ../Salmon.Native.$SALMON_VERSION.zip

cd $CURRDIR