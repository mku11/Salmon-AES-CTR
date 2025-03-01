CURRDIR=$(pwd)

cd ../../libs/projects/salmon-libs-gradle
./gradlew --refresh-dependencies
./gradlew :salmon-ws:bootWar -x test --rerun-tasks
cd salmon-ws
./package.sh

cd $CURRDIR