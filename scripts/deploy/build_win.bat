set CURRDIR=%CD%

cd ..\..\libs\projects\SalmonLibs.VS2022
VsDevCmd.bat & ^
msbuild /property:Configuration=Release & ^
cd %CURRDIR%