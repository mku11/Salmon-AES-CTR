set CURRDIR=%CD%

:: to run type VsDevCmd.bat in the windows console before running this script

cd ..\..\samples\CSharpSamples
msbuild -t:clean & ^
msbuild -t:restore & ^
msbuild /property:Configuration=Debug & ^
cd CSharpSamples\bin\Debug\net9.0
CSharpSamples.exe & ^
cd %CURRDIR%