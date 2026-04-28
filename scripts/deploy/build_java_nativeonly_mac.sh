CURRDIR=$(pwd)

export ENABLE_GPU=true

cd ../../libs/projects/salmon-libs-gradle
./gradlew :salmon-native:clean
./gradlew :salmon-native:build -x test -i -DENABLE_GPU=$ENABLE_GPU
./gradlew :salmon-native:publish -DENABLE_GPU=$ENABLE_GPU
./package.sh

cd $CURRDIR
