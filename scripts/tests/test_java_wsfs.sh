set CURRDIR=$(pwd)

export WS_SERVER_URL=http://localhost:8080
export TEST_DIR="/tmp/salmon/test"
# export ENABLE_GPU=false
export TEST_MODE=WebService

cd ../../libs/projects/salmon-libs-gradle
./gradlew :salmon-fs:test --tests "com.mku.salmon.test.SalmonFSTests" -DTEST_DIR=$TEST_DIR -DTEST_MODE=$TEST_MODE -DHTTP_SERVER_URL=$WS_SERVER_URL -DENABLE_GPU=$ENABLE_GPU -i --rerun-tasks
cd $CURRDIR