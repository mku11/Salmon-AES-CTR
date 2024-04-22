version=2.0.0
rm -rf packages
mkdir packages
rm -rf lib
mkdir lib

SALMON_CORE=salmon-core
SALMON_FS=salmon-fs
SALMON_CORE_LIB=$SALMON_CORE.js.$version
SALMON_FS_LIB=$SALMON_FS.js.$version
SALMON_CORE_LIB_FILENAME=$SALMON_CORE_LIB.zip
SALMON_FS_LIB_FILENAME=$SALMON_FS_LIB.zip

# use local repo
# SALMON_LIBS_URL=http://localhost/repository/javascript
# use salmon release
SALMON_LIBS_URL=https://github.com/mku11/Salmon-AES-CTR/releases/download/v2.0.0

SALMON_CORE_LIB_URL=$SALMON_LIBS_URL/$SALMON_CORE_LIB_FILENAME
SALMON_FS_LIB_URL=$SALMON_LIBS_URL/$SALMON_FS_LIB_FILENAME

cd packages
curl $SALMON_CORE_LIB_URL -LJo $SALMON_CORE_LIB_FILENAME
curl $SALMON_FS_LIB_URL -LJo $SALMON_FS_LIB_FILENAME
 
unzip -qq -o $SALMON_CORE_LIB_FILENAME 
unzip -qq -o $SALMON_FS_LIB_FILENAME 

cd ..
mv packages/$SALMON_CORE lib/
mv packages/$SALMON_FS lib/
