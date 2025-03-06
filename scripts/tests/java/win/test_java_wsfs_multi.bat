set CURRDIR=%CD%

set WS_SERVER_URL=http://localhost:8080
set TEST_DIR="d:/tmp/salmon/test"
set TEST_MODE=WebService
set ENC_THREADS=2

cd ..\..\..\..\libs\projects\salmon-libs-gradle

call gradlew.bat :salmon-fs:test --tests "com.mku.salmon.test.SalmonFSTests" -DTEST_DIR=%TEST_DIR% -DTEST_MODE=%TEST_MODE% -DHTTP_SERVER_URL=%WS_SERVER_URL% -DENC_THREADS=%ENC_THREADS% --rerun-tasks -i
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

cd %CURRDIR%