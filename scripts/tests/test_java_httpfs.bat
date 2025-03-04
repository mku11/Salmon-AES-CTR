set CURRDIR=%CD%

:: set HTTP_SERVER_URL=http://localhost
:: set TEST_DIR="d:/tmp/salmon/test"
:: set ENABLE_GPU=false
set TEST_MODE=Http

cd ..\..\libs\projects\salmon-libs-gradle
gradlew.bat :salmon-fs:test --tests "com.mku.salmon.test.SalmonFSHttpTests" -DTEST_DIR=%TEST_DIR% -DTEST_MODE=%TEST_MODE% -DHTTP_SERVER_URL=%HTTP_SERVER_URL% -DENABLE_GPU=%ENABLE_GPU% -i --rerun-tasks & ^
cd %CURRDIR%