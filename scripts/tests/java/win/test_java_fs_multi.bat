set CURRDIR=%CD%

set TEST_DIR="c:/tmp/salmon/test"
set TEST_MODE=Local
set ENC_THREADS=2

cd ..\..\..\..\libs\projects\salmon-libs-gradle

call gradlew.bat :salmon-fs:test --tests "com.mku.salmon.test.SalmonFSTests" -DTEST_DIR=%TEST_DIR% -DTEST_MODE=%TEST_MODE% -DENC_THREADS=%ENC_THREADS% --rerun-tasks -i
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

cd %CURRDIR%