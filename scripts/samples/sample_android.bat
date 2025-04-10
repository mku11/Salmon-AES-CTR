set CURRDIR=%CD%

cd ..\..\samples\AndroidSamples

call gradlew.bat :salmon-android-samples:run --rerun-tasks -i
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

cd %CURRDIR%