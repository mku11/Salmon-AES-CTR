set CURRDIR=%CD%

cd ..\..\samples\DotNetAndroidSamples\DotNetAndroidSamples

call msbuild -t:Build /p:Configuration=Debug
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

call msbuild -t:Run /p:Configuration=Debug
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

cd %CURRDIR%