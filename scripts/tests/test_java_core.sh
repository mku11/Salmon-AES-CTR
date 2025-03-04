CURRDIR=$(pwd)

# export ENABLE_GPU=false

cd ../../libs/projects/salmon-libs-gradle
./gradlew :salmon-core:test -DENABLE_GPU=$ENABLE_GPU -i --rerun-tasks
cd $CURRDIR