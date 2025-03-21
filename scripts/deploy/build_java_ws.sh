CURRDIR=$(pwd)

cd ../../services/webservice/project
./gradlew --refresh-dependencies
./gradlew :salmon-ws:bootWar -x test --rerun-tasks
cd salmon-ws
./package.sh

cd $CURRDIR