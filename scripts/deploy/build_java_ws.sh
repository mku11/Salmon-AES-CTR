CURRDIR=$(pwd)

cd ../../services/webservice/project
./gradlew --refresh-dependencies
./gradlew :salmon-ws:bootWar -x test -i
cd salmon-ws
./package.sh

cd $CURRDIR