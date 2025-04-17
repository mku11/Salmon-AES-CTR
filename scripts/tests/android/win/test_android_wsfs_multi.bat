set CURRDIR=%CD%

set WS_SERVER_URL=http://192.168.1.4:8080
set TEST_MODE=WebService
set ENC_THREADS=2

cd ..\..\..\..\libs\projects\salmon-libs-gradle-android

call gradlew.bat :salmon-fs-android-test:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mku.salmon.test.SalmonFSAndroidTests -DTEST_MODE=%TEST_MODE% -DWS_SERVER_URL=%WS_SERVER_URL% -DENC_THREADS=%ENC_THREADS% -i
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

cd %CURRDIR%