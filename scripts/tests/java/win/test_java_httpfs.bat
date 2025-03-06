set CURRDIR=%CD%

set HTTP_SERVER_URL=http://localhost:8000
set TEST_DIR="d:\tmp\salmon\test"
set TEST_MODE=Http

cd ..\..\..\..\libs\projects\salmon-libs-gradle

call gradlew.bat :salmon-fs:test --tests "com.mku.salmon.test.SalmonFSHttpTests" -DTEST_DIR=%TEST_DIR% -DTEST_MODE=%TEST_MODE% -DHTTP_SERVER_URL=%HTTP_SERVER_URL% --rerun-tasks -i
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

cd %CURRDIR%