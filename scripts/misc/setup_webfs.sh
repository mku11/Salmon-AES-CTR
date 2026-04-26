CURRDIR=$(pwd)

cd ../../libs/deps/WebFS/project
./gradlew bootWar
cd webfs-service
./package.sh
cd ../../output/webfs-service/webfs-service/config
cp -f ../../../../../../test/config/application.properties.linux.macos.test application.properties

cd $CURRDIR