CURRDIR=$(pwd)

export TEST_DIR="/tmp/salmon/test"
export TEST_MODE=Local

cd ../../../../libs/projects/salmon-libs-gradle

./gradlew :salmon-fs:test --tests "com.mku.salmon.test.SalmonFSTests" -DTEST_DIR=$TEST_DIR -DTEST_MODE=$TEST_MODE -DENABLE_GPU=$ENABLE_GPU -i --rerun-tasks

cd $CURRDIR