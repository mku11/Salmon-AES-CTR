CURRDIR=$(pwd)
SALMON_VERSION=3.0.0

mkdir -p /tmp/salmon
cd ../../output/java-ws/java-ws.$SALMON_VERSION
./start-salmon-ws.sh
cd $CURRDIR