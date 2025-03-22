set CURRDIR=%CD%

set NODE_OPTIONS=--experimental-vm-modules
set ENC_THREADS=1

cd ..\..\..\..\libs\projects\SalmonLibs.vscode

call npm run test -- salmon-core -t="salmon-core" ENC_THREADS=%ENC_THREADS%
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

cd %CURRDIR%