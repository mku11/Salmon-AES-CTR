set CURRDIR=%CD%

:: ALL
call test_python_coregpu.bat
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

call test_python_coregpu_multi.bat
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

call test_python_nativegpu.bat
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

call test_python_nativegpu_multi.bat
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

cd %CURRDIR%