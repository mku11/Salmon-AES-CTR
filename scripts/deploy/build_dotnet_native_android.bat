set CURRDIR=%CD%

:: to run type VsDevCmd.bat in the windows console before running this script

cd ..\..\libs\projects\SalmonLibs.VS2022
:: msbuild /property:Configuration=Debug & ^
msbuild /property:Configuration=Release & ^
cd %CURRDIR%