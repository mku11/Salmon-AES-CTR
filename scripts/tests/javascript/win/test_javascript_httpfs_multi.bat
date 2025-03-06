set CURRDIR=%CD%

set HTTP_SERVER_URL=http://localhost:8000
set TEST_DIR="d:\tmp\salmon\test"
set TEST_MODE=Http
set ENC_THREADS=2

cd ..\..\..\..\libs\projects\SalmonLibs.vscode

call npm run test -- salmon-fs -t="salmon-httpfs" TEST_DIR=%TEST_DIR% TEST_MODE=%TEST_MODE% HTTP_SERVER_URL=%HTTP_SERVER_URL% ENC_THREADS=%ENC_THREADS%
if %ERRORLEVEL% GEQ 1 cd %CURRDIR% && EXIT /B 1

cd %CURRDIR%