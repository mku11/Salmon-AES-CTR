set CURRDIR=%CD%

set WS_SERVER_URL=http://localhost:8080
set TEST_DIR="d:/tmp/salmon/test"
:: set ENABLE_GPU=false
set TEST_MODE=WebService

cd ..\..\libs\projects\salmon-libs-gradle
gradlew.bat :salmon-fs:test --tests "com.mku.salmon.test.SalmonFSTests" -DTEST_DIR=%TEST_DIR% -DTEST_MODE=%TEST_MODE% -DHTTP_SERVER_URL=%WS_SERVER_URL% -DENABLE_GPU=%ENABLE_GPU% -i --rerun-tasks & ^
cd %CURRDIR%