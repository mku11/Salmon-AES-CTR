set CURRDIR=%CD%

set HTTP_SERVER_URL=http://localhost:8000
set TEST_DIR=d:\tmp\salmon\test
:: set ENABLE_GPU=false
set TEST_MODE=Http

cd ..\..\libs\projects\SalmonLibs.VS2022
VsDevCmd.bat & ^
msbuild /property:Configuration=Debug & ^
vstest.console Salmon.Test\bin\Debug\net8.0-windows\Salmon.Test.dll /Tests:SalmonFSHttpTests /Logger:Console & ^
cd %CURRDIR%