set CURRDIR=%CD%

cd ..\..\..\..\libs\projects\salmon-libs-gradle

call gradlew.bat :salmon-core:jmh --rerun-tasks -i
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

cd %CURRDIR%