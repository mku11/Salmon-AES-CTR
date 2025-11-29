CURRDIR=$cwd
TEST_DIR=/tmp/salmon

mkdir -p $TEST_DIR/test
npx http-server /tmp/salmon -p 8880
cd $CURRDIR