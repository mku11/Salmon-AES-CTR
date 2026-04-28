@echo ON
set CURRDIR=%CD%

cd ..\..\libs\projects\salmon-libs-gradle
gradlew.bat build -x test -i & ^
gradlew.bat publish -i & ^
package.bat & ^
cd %CURRDIR%