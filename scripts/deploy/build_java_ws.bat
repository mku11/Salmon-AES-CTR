set CURRDIR=%CD%

cd ..\..\libs\projects\salmon-libs-gradle
gradlew.bat --refresh-dependencies && ^
gradlew.bat :salmon-ws:bootWar -x test --rerun-tasks && ^
cd salmon-ws && ^
package.bat && ^
cd %CURRDIR%