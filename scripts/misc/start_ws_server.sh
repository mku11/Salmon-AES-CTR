CURRDIR=$(pwd)

WEBFS_VERSION=1.0.0

mkdir -p /tmp/salmon
cd ../../libs/deps/WebFS/output/webfs-service/webfs-service-$WEBFS_VERSION
./start-webfs-service.sh
cd $CURRDIR