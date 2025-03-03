@ECHO OFF
set CURRDIR=%CD%

cd ..\..\libs\projects\SalmonLibs.vscode & ^
npm install & ^
npm run build & ^
npm run test -- salmon-core -t="salmon-core" & ^
cd %CURRDIR%