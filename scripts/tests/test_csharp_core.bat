set CURRDIR=%CD%

:: to run type VsDevCmd.bat in the windows console before running this script

:: set ENABLE_GPU=false

cd ..\..\libs\projects\SalmonLibs.VS2022
msbuild /property:Configuration=Debug & ^
vstest.console Salmon.Test\bin\Debug\net8.0-windows\Salmon.Test.dll /Tests:SalmonCoreTests /Logger:Console & ^
cd %CURRDIR%