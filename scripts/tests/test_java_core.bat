set CURRDIR=%CD%

:: set ENABLE_GPU=false

cd ..\..\libs\projects\salmon-libs-gradle
gradlew.bat :salmon-native:build --rerun-tasks & ^
gradlew.bat :salmon-core:test -DENABLE_GPU=%ENABLE_GPU% -i --rerun-tasks & ^
cd %CURRDIR%