SALMON_VERSION=3.0.1
rm -rf packages
mkdir packages
rm -rf assets/js/lib
mkdir assets/js/lib

SALMON_CORE=salmon-core
SALMON_FS=salmon-fs
SALMON_CORE_LIB=$SALMON_CORE.js.$SALMON_VERSION
SALMON_FS_LIB=$SALMON_FS.js.$SALMON_VERSION
SALMON_CORE_LIB_FILENAME=$SALMON_CORE_LIB.zip
SALMON_FS_LIB_FILENAME=$SALMON_FS_LIB.zip

# use local repo
SALMON_LIBS_URL=http://localhost/repository/javascript
# use salmon official github releases
# SALMON_LIBS_URL=https://github.com/mku11/Salmon-AES-CTR/releases/download/v$SALMON_VERSION

SALMON_CORE_LIB_URL=$SALMON_LIBS_URL/$SALMON_CORE_LIB_FILENAME
SALMON_FS_LIB_URL=$SALMON_LIBS_URL/$SALMON_FS_LIB_FILENAME

cd packages
curl $SALMON_CORE_LIB_URL -LJo $SALMON_CORE_LIB_FILENAME
curl $SALMON_FS_LIB_URL -LJo $SALMON_FS_LIB_FILENAME
 
unzip -qq -o $SALMON_CORE_LIB_FILENAME 
unzip -qq -o $SALMON_FS_LIB_FILENAME 

cd ..
cp -rf packages/$SALMON_CORE assets/js/lib/
rm -rf packages/$SALMON_CORE
cp -rf packages/$SALMON_FS assets/js/lib/
rm -rf packages/$SALMON_FS