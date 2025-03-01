CURRDIR=$(pwd)

cd ../../libs/projects/salmon-libs-gradle
./gradlew --refresh-dependencies
./gradlew assembleRelease -x test --rerun-tasks
./gradlew publish --rerun-tasks

cd $CURRDIR