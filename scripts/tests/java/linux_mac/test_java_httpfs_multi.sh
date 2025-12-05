CURRDIR=$(pwd)

export ENC_THREADS=2
export HTTP_SERVER_URL=http://localhost:8880
export TEST_DIR="/tmp/salmon/test"
export TEST_MODE=Http

cd ../../../../libs/projects/salmon-libs-gradle

./gradlew :salmon-fs:test --tests "com.mku.salmon.test.SalmonFSHttpTests" -DTEST_DIR=$TEST_DIR -DTEST_MODE=$TEST_MODE -DHTTP_SERVER_URL=$HTTP_SERVER_URL -DENC_THREADS=$ENC_THREADS -i --rerun-tasks

cd $CURRDIR