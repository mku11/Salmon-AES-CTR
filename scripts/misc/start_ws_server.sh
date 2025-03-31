CURRDIR=$(pwd)
SALMON_VERSION=3.0.1

mkdir -p /tmp/salmon
cd ../../output/java-ws/java-ws.$SALMON_VERSION
./start-salmon-ws.sh
cd $CURRDIR