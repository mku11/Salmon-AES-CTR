set CURRDIR=%CD%

cd ..\..\samples\JavaSamples
gradlew.bat clean & ^
gradlew.bat --refresh-dependencies & ^
gradlew.bat build -x test --rerun-tasks & ^
gradlew.bat run & ^
cd %CURRDIR%