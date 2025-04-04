set CURRDIR=%CD%

set TEST_MODE=Local

cd ..\..\..\..\libs\projects\salmon-libs-gradle-android

call gradlew.bat :salmon-fs-android-test:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class='com.mku.salmon.test.SalmonFSAndroidTests' -DTEST_MODE=%TEST_MODE% --rerun-tasks -i
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

cd %CURRDIR%