CURRDIR=$(pwd)

export ENABLE_GPU=true

cd ../../libs/projects/salmon-libs-gradle

./gradlew build -x test -i -DENABLE_GPU=$ENABLE_GPU 
./gradlew publish -DENABLE_GPU=$ENABLE_GPU
./package.sh

cd $CURRDIR
