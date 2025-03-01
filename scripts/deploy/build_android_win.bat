set CURRDIR=%CD%

cd ..\..\libs\projects\salmon-libs-gradle-android
gradlew.bat --refresh-dependencies && ^
gradlew.bat assembleRelease -x test --rerun-tasks && ^
gradlew.bat publish --rerun-tasks && ^
cd %CURRDIR%