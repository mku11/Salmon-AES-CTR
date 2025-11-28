@echo ON
set CURRDIR=%CD%
set TEST_DIR=c:\tmp\salmon

powershell mkdir -ErrorAction SilentlyContinue %TEST_DIR%\test &^
npx http-server C:\tmp\salmon -p 8880 &^
cd %CURRDIR%