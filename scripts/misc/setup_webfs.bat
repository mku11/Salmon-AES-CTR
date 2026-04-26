@echo ON
set CURRDIR=%CD%

cd ..\..\libs\deps\WebFS\project &^
gradlew.bat bootWar &^
cd webfs-service &^
call package.bat &^
cd ..\..\output\webfs-service\webfs-service\config &^
copy /Y ..\..\..\..\..\..\test\config\application.properties.win.test application.properties &^
cd %CURRDIR%