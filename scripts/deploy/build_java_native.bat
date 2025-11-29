@echo ON
set CURRDIR=%CD%

cd ..\..\libs\projects\salmon-libs-gradle
gradlew.bat clean & ^
gradlew.bat --refresh-dependencies & ^
gradlew.bat build -x test --rerun-tasks -i & ^
gradlew.bat publish --rerun-tasks -i & ^
package.bat & ^
cd %CURRDIR%