set CURRDIR=%CD%

:: to run type VsDevCmd.bat in the windows console before running this script

cd ..\..\..\..\libs\projects\SalmonLibs.VS2022

call vstest.console x64\DebugGPU\Salmon.Native.Test.dll /Tests:SalmonNativeTestPerf /Logger:Console;verbosity=detailed
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

cd %CURRDIR%