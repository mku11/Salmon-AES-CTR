@ECHO ON
set CURRDIR=%CD%

:: dependencies
set SIMPLEIO_VERSION=1.0.1
set SIMPLEFS_VERSION=1.0.1

:: source settings
source %CURRDIR%\settings.cfg

:: get submodules
git submodule update --recursive --init

:: get specific versions
cd %CURRDIR%\..\..\libs\deps\SimpleIO
git fetch --tags
git pull origin main
git checkout v%SIMPLEIO_VERSION%
xcopy /E /Y /I src\csharp\SimpleIO ..\..\src\csharp\SimpleIO
xcopy /E /Y /I src\java\simple-io ..\..\src\java\simple-io
xcopy /E /Y /I src\python\simple_io ..\..\src\python\simple_io
xcopy /E /Y /I src\typescript\simple-io ..\..\src\typescript\simple-io
xcopy /E /Y /I src\android\simple-io ..\..\src\android\simple-io

cd %CURRDIR%\..\..\libs\deps\SimpleFS
git fetch --tags
git pull origin main
git checkout v%SIMPLEFS_VERSION%
xcopy /E /Y /I src\csharp\SimpleFS ..\..\src\csharp\SimpleFS
xcopy /E /Y /I src\java\simple-fs ..\..\src\java\simple-fs
xcopy /E /Y /I src\python\simple_fs ..\..\src\python\simple_fs
xcopy /E /Y /I src\typescript\simple-fs ..\..\src\typescript\simple-fs
xcopy /E /Y /I src\android\simple-fs ..\..\src\android\simple-fs
xcopy /E /Y /I src\dotnetandroid\simple-fs ..\..\src\dotnetandroid\simple-fs
cd %CURRDIR%