CURRDIR=$(pwd)

cd ../../libs/projects/salmon-libs-gradle-android
./gradlew assembleRelease -x test -i
./gradlew publish

cd $CURRDIR