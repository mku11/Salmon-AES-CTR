set CURRDIR=%CD%

:: to run type VsDevCmd.bat in the windows console before running this script

set ENABLE_GPU=false
set ENC_THREADS=1

cd ..\..\..\..\libs\projects\SalmonLibs.VS2022

call vstest.console Salmon.Test\bin\Debug\net8.0-windows\Salmon.Test.dll /Tests:SalmonCorePerfTests /Logger:Console;Verbosity=Detailed
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

cd %CURRDIR%