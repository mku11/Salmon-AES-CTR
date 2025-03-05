set CURRDIR=%CD%

:: set ENABLE_GPU=false

cd ..\..\libs\projects\SalmonLibs.VS2022
VsDevCmd.bat & ^
msbuild /property:Configuration=Debug & ^
vstest.console Salmon.Test\bin\Debug\net8.0-windows\Salmon.Test.dll /Tests:SalmonCoreTests /Logger:Console & ^
cd %CURRDIR%