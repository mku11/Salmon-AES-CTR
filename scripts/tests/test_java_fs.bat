set CURRDIR=%CD%

set TEST_DIR="d:/tmp/salmon/test"
:: set ENABLE_GPU=false
set TEST_MODE=Local

cd ..\..\libs\projects\salmon-libs-gradle
gradlew.bat :salmon-fs:test --tests "com.mku.salmon.test.SalmonFSTests" -DTEST_DIR=%TEST_DIR% -DTEST_MODE=%TEST_MODE% -DENABLE_GPU=%ENABLE_GPU% -i --rerun-tasks & ^
cd %CURRDIR%