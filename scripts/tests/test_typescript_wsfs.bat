@ECHO OFF
set CURRDIR=%CD%

:: set WS_SERVER_URL=http://localhost:8080
:: set TEST_DIR="d:\tmp\salmon\test"
set TEST_MODE=WebService

cd ..\..\libs\projects\SalmonLibs.vscode & ^
npm install & ^
npm run build & ^
npm run test -- salmon-fs -t="salmon-fs" TEST_DIR=%TEST_DIR% TEST_MODE=%TEST_MODE% WS_SERVER_URL=%WS_SERVER_URL% & ^
cd %CURRDIR%