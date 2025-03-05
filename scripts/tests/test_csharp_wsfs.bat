set CURRDIR=%CD%

:: to run type VsDevCmd.bat in the windows console before running this script

set WS_SERVER_URL=http://localhost:8080
set TEST_DIR=d:\tmp\salmon\test
:: set ENABLE_GPU=false
set TEST_MODE=WebService

cd ..\..\libs\projects\SalmonLibs.VS2022
msbuild /property:Configuration=Debug & ^
vstest.console Salmon.Test\bin\Debug\net8.0-windows\Salmon.Test.dll /Tests:SalmonFSTests /Logger:Console & ^
cd %CURRDIR%