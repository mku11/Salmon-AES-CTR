set CURRDIR=%CD%

set NODE_OPTIONS=--experimental-vm-modules

:: ALL
call test_javascript_coregpu.bat
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

call test_javascript_coregpu_multi.bat
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

cd %CURRDIR%