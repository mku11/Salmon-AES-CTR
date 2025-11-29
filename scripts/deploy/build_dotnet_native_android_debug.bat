set CURRDIR=%CD%

:: to run type VsDevCmd.bat in the windows console before running this script

cd ..\..\libs\projects\SalmonLibs.VS2022
msbuild -t:restore & ^
msbuild /property:Configuration=Debug /property:Platform=x64 & ^
cd %CURRDIR%