CURRDIR=$(pwd)

mkdir -p /tmp/salmon
cd ../../libs/deps/WebFS/output/webfs-service/webfs-service
./start-webfs-service.sh
cd $CURRDIR