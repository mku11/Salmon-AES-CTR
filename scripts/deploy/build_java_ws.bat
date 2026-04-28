set CURRDIR=%CD%

cd ..\..\services\webservice\project & ^
gradlew.bat :salmon-ws:bootWar -x test -i & ^
cd salmon-ws & ^
package.bat & ^
cd %CURRDIR%