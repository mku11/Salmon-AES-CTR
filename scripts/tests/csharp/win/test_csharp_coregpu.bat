set CURRDIR=%CD%

:: to run type VsDevCmd.bat in the windows console before running this script

set ENABLE_GPU=true
set ENC_THREADS=1

cd ..\..\..\..\libs\projects\SalmonLibs.VS2022

set AES_PROVIDER_TYPE=AesGPU
call vstest.console Salmon.Test\bin\DebugGPU\net9.0-windows\Salmon.Test.dll /Tests:SalmonCoreTests /Logger:Console;Verbosity=Detailed
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

cd %CURRDIR%