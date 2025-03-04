@ECHO OFF
set CURRDIR=%CD%

set HTTP_SERVER_URL=http://localhost:8000
set TEST_DIR="d:\tmp\salmon\test"
set TEST_MODE=Http

cd ..\..\libs\projects\SalmonLibs.vscode & ^
npm install & ^
npm run build & ^
npm run test -- salmon-fs -t="salmon-httpfs" TEST_DIR=%TEST_DIR% TEST_MODE=%TEST_MODE% HTTP_SERVER_URL=%HTTP_SERVER_URL% & ^
cd %CURRDIR%