@ECHO ON
set CURRDIR=%CD%

:: get submodules
git submodule update --recursive --init

:: get specific versions
cd %CURRDIR%\..\..\libs\deps\SimpleIO
xcopy /E /Y /I src\csharp\SimpleIO ..\..\src\csharp\SimpleIO
xcopy /E /Y /I src\java\simple-io ..\..\src\java\simple-io
xcopy /E /Y /I src\python\simple_io ..\..\src\python\simple_io
xcopy /E /Y /I src\typescript\simple-io ..\..\src\typescript\simple-io
xcopy /E /Y /I src\android\simple-io ..\..\src\android\simple-io

cd %CURRDIR%\..\..\libs\deps\SimpleFS
xcopy /E /Y /I src\csharp\SimpleFS ..\..\src\csharp\SimpleFS
xcopy /E /Y /I src\java\simple-fs ..\..\src\java\simple-fs
xcopy /E /Y /I src\python\simple_fs ..\..\src\python\simple_fs
xcopy /E /Y /I src\typescript\simple-fs ..\..\src\typescript\simple-fs
xcopy /E /Y /I src\android\simple-fs ..\..\src\android\simple-fs
xcopy /E /Y /I src\dotnetandroid\simple-fs ..\..\src\dotnetandroid\simple-fs

cd %CURRDIR%\..\..\libs\deps\WebGPULogger
xcopy /E /Y /I src\javascript\webgpu-logger ..\..\src\javascript\webgpu-logger

cd %CURRDIR%\..\..\libs\deps\Best
xcopy /E /Y /I src ..\..\..\libs\projects\SalmonLibs.vscode\best

cd %CURRDIR%