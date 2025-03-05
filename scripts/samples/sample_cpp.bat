set CURRDIR=%CD%

:: to run type VsDevCmd.bat in the windows console before running this script

cd ..\..\samples\CppSamples
msbuild -t:clean & ^
msbuild -p:RestorePackagesConfig=true -t:restore & ^
msbuild /property:Configuration=Debug & ^
cd x64\Debug
CppSamples.exe & ^
cd %CURRDIR%