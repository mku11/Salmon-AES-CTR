set CURRDIR=%CD%

cd ..\..\libs\projects\SalmonLibs.vscode
npm run build && ^
package.bat && ^
cd %CURRDIR%