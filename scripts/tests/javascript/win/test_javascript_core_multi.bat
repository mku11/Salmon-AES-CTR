set CURRDIR=%CD%

set ENC_THREADS=2

cd ..\..\..\..\libs\projects\SalmonLibs.vscode

call npm run test -- salmon-core -t="salmon-core" ENC_THREADS=%ENC_THREADS%
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

cd %CURRDIR%