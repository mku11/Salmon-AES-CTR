set CURRDIR=%CD%

set NODE_OPTIONS=--experimental-vm-modules
set TEST_DIR="d:\tmp\salmon\test"
set TEST_MODE=Node
set ENC_THREADS=2

cd ..\..\..\..\libs\projects\SalmonLibs.vscode

call npm run test -- salmon-fs -t="salmon-fs" TEST_DIR=%TEST_DIR% TEST_MODE=%TEST_MODE% ENC_THREADS=%ENC_THREADS%
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

cd %CURRDIR%