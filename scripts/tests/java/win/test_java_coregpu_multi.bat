set CURRDIR=%CD%

set ENC_THREADS=2

cd ..\..\..\..\libs\projects\salmon-libs-gradle

call gradlew.bat :salmon-core:test --tests "com.mku.salmon.test.SalmonCoreTests" -DAES_PROVIDER_TYPE=AesGPU -DENC_THREADS=%ENC_THREADS%  --rerun-tasks -i
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

cd %CURRDIR%