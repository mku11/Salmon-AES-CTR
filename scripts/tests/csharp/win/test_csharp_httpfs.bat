set CURRDIR=%CD%

:: to run type VsDevCmd.bat in the windows console before running this script

set HTTP_SERVER_URL=http://localhost
set TEST_DIR=d:\tmp\salmon\test
set TEST_MODE=Http
set ENC_THREADS=1
set ENABLE_GPU=false
set AES_PROVIDER_TYPE=Default

cd ..\..\..\..\libs\projects\SalmonLibs.VS2022

call vstest.console Salmon.Test\bin\Debug\net9.0-windows\Salmon.Test.dll /Tests:SalmonFSHttpTests /Logger:Console;Verbosity=Detailed
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

cd %CURRDIR%