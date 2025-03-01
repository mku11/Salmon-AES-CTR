set CURRDIR=%CD%

cd ..\..\libs\projects\SalmonLibs.VS2022
:: Build for x86_64 with GPU support (OpenCL)
VsDevCmd.bat && ^
msbuild /property:Configuration=ReleaseGPU && ^
cd %CURRDIR%