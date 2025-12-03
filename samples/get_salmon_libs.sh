DEPS_DIR=./libs/
SALMON_LIB_VERSION=3.0.2

SALMON_BINARY=salmon-multi-arch.v$SALMON_LIB_VERSION.zip
SALMON_URL=https://github.com/mku11/Salmon-AES-CTR/releases/download/v$SALMON_LIB_VERSION/$SALMON_BINARY
ZIP_FILENAME=salmon.zip

mkdir -p $DEPS_DIR
curl $SALMON_URL -LJo $DEPS_DIR/$ZIP_FILENAME
cd $DEPS_DIR
unzip -qq -o $ZIP_FILENAME
