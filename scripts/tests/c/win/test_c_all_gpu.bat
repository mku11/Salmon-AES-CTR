set CURRDIR=%CD%

:: ALL
call test_c_gpu.bat
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

cd %CURRDIR%