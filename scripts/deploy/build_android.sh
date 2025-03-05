CURRDIR=$(pwd)

cd ../../libs/projects/salmon-libs-gradle-android
./gradlew --refresh-dependencies
./gradlew assembleRelease -x test --rerun-tasks
./gradlew publish --rerun-tasks

cd $CURRDIR