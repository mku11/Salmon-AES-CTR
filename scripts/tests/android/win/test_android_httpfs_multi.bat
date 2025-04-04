set CURRDIR=%CD%

set HTTP_SERVER_URL=http://localhost:8000
set TEST_MODE=Http
set ENC_THREADS=2

cd ..\..\..\..\libs\projects\salmon-libs-gradle-android

call gradlew.bat :salmon-fs-android-test:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class='com.mku.salmon.test.SalmonFSAndroidTests' -DTEST_MODE=%TEST_MODE% -DHTTP_SERVER_URL=%HTTP_SERVER_URL% -DENC_THREADS=%ENC_THREADS% --rerun-tasks -i
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

cd %CURRDIR%