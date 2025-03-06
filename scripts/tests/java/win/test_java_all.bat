set CURRDIR=%CD%

:: ALL
call test_java_core.bat
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

echo %CD%
call test_java_core_multi.bat
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

call test_java_fs.bat
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

call test_java_fs_multi.bat
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

call test_java_httpfs.bat
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

call test_java_httpfs_multi.bat
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

call test_java_native.bat
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

call test_java_native_multi.bat
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

call test_java_wsfs.bat
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

call test_java_wsfs_multi.bat
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

cd %CURRDIR%