set CURRDIR=%CD%

cd ..\..\libs\projects\salmon-libs-gradle
gradlew.bat clean & ^
gradlew.bat --refresh-dependencies & ^
gradlew.bat build -x test --rerun-tasks & ^
gradlew.bat publish --rerun-tasks & ^
package.bat & ^
cd %CURRDIR%