set CURRDIR=%CD%

:: to run type VsDevCmd.bat in the windows console before running this script

cd ..\..\services\winservice\project
msbuild /property:Configuration=Release & ^
cd %CURRDIR%