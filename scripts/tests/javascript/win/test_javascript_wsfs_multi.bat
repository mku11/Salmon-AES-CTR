set CURRDIR=%CD%

set WS_SERVER_URL=http://localhost:8080
set TEST_DIR="d:\tmp\salmon\test"
set TEST_MODE=WebService
set ENC_THREADS=2

cd ..\..\..\..\libs\projects\SalmonLibs.vscode

call npm run test -- salmon-fs -t="salmon-fs" TEST_DIR=%TEST_DIR% TEST_MODE=%TEST_MODE% WS_SERVER_URL=%WS_SERVER_URL%  ENC_THREADS=%ENC_THREADS%
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

cd %CURRDIR%