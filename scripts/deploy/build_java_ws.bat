set CURRDIR=%CD%

cd ..\..\services\webservice\project & ^
gradlew.bat --refresh-dependencies & ^
gradlew.bat :salmon-ws:bootWar -x test --rerun-tasks & ^
cd salmon-ws & ^
package.bat & ^
cd %CURRDIR%