set CURRDIR=%CD%

:: to run type VsDevCmd.bat in the windows console before running this script

cd ..\..\..\..\libs\projects\SalmonLibs.VS2022

call msbuild /property:Configuration=DebugGPU /p:Platform=x64
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

cd %CURRDIR%
