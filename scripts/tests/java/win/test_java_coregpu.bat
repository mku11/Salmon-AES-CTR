set CURRDIR=%CD%

cd ..\..\..\..\libs\projects\salmon-libs-gradle
call gradlew.bat :salmon-core:test --tests "com.mku.salmon.test.SalmonCoreTests" -DAES_PROVIDER_TYPE=AesGPU --rerun-tasks -i
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

cd %CURRDIR%