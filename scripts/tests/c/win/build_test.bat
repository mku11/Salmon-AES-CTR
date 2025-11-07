set CURRDIR=%CD%

cd ..\..\..\..\libs\projects\SalmonLibs.VS2022

call msbuild SalmonLibs.VS2022.sln /t:SalmonNative /p:Configuration=Debug /p:Platform=x64
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

call msbuild SalmonLibs.VS2022.sln /t:Salmon_Native_Test /p:Configuration=Debug /p:Platform=x64
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

cd %CURRDIR%
