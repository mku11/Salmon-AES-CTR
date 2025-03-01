set CURRDIR=%CD%

cd ..\..\services\winservice\project
VsDevCmd.bat && ^
msbuild /property:Configuration=Release && ^
cd %CURRDIR%