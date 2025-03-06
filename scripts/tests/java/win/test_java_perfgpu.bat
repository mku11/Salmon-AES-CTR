set CURRDIR=%CD%

set ENABLE_GPU=true

cd ..\..\..\..\libs\projects\salmon-libs-gradle

call gradlew.bat :salmon-core:test --tests "com.mku.salmon.test.SalmonCorePerfTests" -DENABLE_GPU=%ENABLE_GPU% --rerun-tasks -i
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

cd %CURRDIR%