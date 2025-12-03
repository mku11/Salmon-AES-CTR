CURRDIR=$(pwd)

export ENABLE_GPU=true

cd ../../libs/projects/salmon-libs-gradle

./gradlew --refresh-dependencies
./gradlew build -x test -i --rerun-tasks -DENABLE_GPU=$ENABLE_GPU 
./gradlew publish --rerun-tasks -DENABLE_GPU=$ENABLE_GPU
./package.sh

cd $CURRDIR
