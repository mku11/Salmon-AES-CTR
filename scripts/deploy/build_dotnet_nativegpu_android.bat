:: Build for x86_64 with GPU support (OpenCL)
set CURRDIR=%CD%

:: to run type VsDevCmd.bat in the windows console before running this script

cd ..\..\libs\projects\SalmonLibs.VS2022
msbuild -t:restore /property:Configuration=ReleaseGPU /property:Platform=x64 & ^
msbuild /property:Configuration=ReleaseGPU /property:Platform=x64 & ^
cd %CURRDIR%