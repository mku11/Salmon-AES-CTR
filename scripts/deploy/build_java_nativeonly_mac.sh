CURRDIR=$(pwd)

export ENABLE_GPU=true

cd ../../libs/projects/salmon-libs-gradle
./gradlew :salmon-native:clean
./gradlew --refresh-dependencies
./gradlew :salmon-native:build -x test -i --rerun-tasks -DENABLE_GPU=$ENABLE_GPU
./gradlew :salmon-native:publish --rerun-tasks -DENABLE_GPU=$ENABLE_GPU
./package.sh

cd $CURRDIR
