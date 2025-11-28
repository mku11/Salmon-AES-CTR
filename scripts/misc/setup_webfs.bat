@echo ON
set CURRDIR=%CD%

cd ..\..\libs\deps\WebFS\project &^
gradlew.bat bootWar &^
cd webfs-service &^
call package.bat &^
cd ..\..\output\webfs-service\webfs-service-1.0.0\config &^
copy /Y ..\..\..\..\..\..\test\config\application.properties.win.test application.properties
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

cd %CURRDIR%