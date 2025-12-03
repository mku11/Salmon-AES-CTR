@echo ON
set CURRDIR=%CD%
set ENABLE_GPU=true

cd ..\..\libs\projects\salmon-libs-gradle
gradlew.bat --refresh-dependencies & ^
gradlew.bat build -x test --rerun-tasks -DENABLE_GPU=%ENABLE_GPU% -i & ^
gradlew.bat publish --rerun-tasks -DENABLE_GPU=%ENABLE_GPU% -i & ^
package.bat & ^
cd %CURRDIR%