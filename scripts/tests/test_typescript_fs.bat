@ECHO OFF
set CURRDIR=%CD%

:: set TEST_DIR="d:\tmp\salmon\test"
set TEST_MODE=Node

cd ..\..\libs\projects\SalmonLibs.vscode & ^
npm install & ^
npm run build & ^
npm run test -- salmon-fs -t="salmon-fs" TEST_DIR=%TEST_DIR% TEST_MODE=%TEST_MODE% & ^
cd %CURRDIR%