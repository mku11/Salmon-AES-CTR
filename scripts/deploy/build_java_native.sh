CURRDIR=$(pwd)

cd ../../libs/projects/salmon-libs-gradle
./gradlew clean
./gradlew build -x test
./gradlew publish -i
./package.sh

cd $CURRDIR