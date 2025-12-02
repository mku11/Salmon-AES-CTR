@ECHO ON
set CURRDIR=%CD%

echo Setting up Salmon for development
cd .\scripts\misc
init_gitsubmodules.bat
get_opencl.bat

cd %CURRDIR%