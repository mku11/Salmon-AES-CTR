set CURRDIR=%CD%

set TEST_DIR=d:\tmp\salmon\test
:: set ENABLE_GPU=false
set TEST_MODE=Local

cd ..\..\libs\projects\SalmonLibs.VS2022
VsDevCmd.bat & ^
msbuild /property:Configuration=Debug & ^
vstest.console Salmon.Test\bin\Debug\net8.0-windows\Salmon.Test.dll /Tests:SalmonFSTests /Logger:Console & ^
cd %CURRDIR%