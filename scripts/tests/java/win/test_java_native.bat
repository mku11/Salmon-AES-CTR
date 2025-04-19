set CURRDIR=%CD%

cd ..\..\..\..\libs\projects\salmon-libs-gradle

call gradlew.bat :salmon-core:test --tests "com.mku.salmon.test.SalmonNativeTests" -DAES_PROVIDER_TYPE=Aes -i --rerun-tasks -i
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

call gradlew.bat :salmon-core:test --tests "com.mku.salmon.test.SalmonNativeTests" -DAES_PROVIDER_TYPE=AesIntrinsics --rerun-tasks -i
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

cd %CURRDIR%