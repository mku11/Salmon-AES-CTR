@echo ON
set CURRDIR=%CD%
set ENABLE_GPU=true

cd ..\..\libs\projects\salmon-libs-gradle
gradlew.bat build -x test -DENABLE_GPU=%ENABLE_GPU% -i & ^
gradlew.bat publish -DENABLE_GPU=%ENABLE_GPU% -i & ^
package.bat & ^
cd %CURRDIR%