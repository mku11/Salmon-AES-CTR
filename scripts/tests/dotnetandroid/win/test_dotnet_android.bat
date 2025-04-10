set CURRDIR=%CD%

cd ..\..\..\..\libs\projects\SalmonLibs.VS2022\SalmonFSAndroidTest

call msbuild -t:Build /p:Configuration=Debug
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

call msbuild -t:Run /p:Configuration=Debug
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

cd %CURRDIR%