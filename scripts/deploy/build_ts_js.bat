set CURRDIR=%CD%

cd ..\..\libs\projects\SalmonLibs.vscode
npm install & ^
npm run build & ^
package.bat & ^
cd %CURRDIR%