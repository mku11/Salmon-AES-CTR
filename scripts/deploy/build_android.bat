set CURRDIR=%CD%

cd ..\..\libs\projects\salmon-libs-gradle-android
gradlew.bat assembleRelease -x test -i & ^
gradlew.bat publish & ^
cd %CURRDIR%