set CURRDIR=%CD%

set WS_SERVER_URL=http://localhost:8080
set TEST_MODE=WebService
set ENC_THREADS=2

cd ..\..\..\..\libs\projects\salmon-libs-gradle

call gradlew.bat :salmon-fs-android-test:test --tests "com.mku.salmon.test.SalmonFSAndroidTests" -DTEST_MODE=%TEST_MODE% -DWS_SERVER_URL=%WS_SERVER_URL% -DENC_THREADS=%ENC_THREADS% --rerun-tasks -i
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

cd %CURRDIR%