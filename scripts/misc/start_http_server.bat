@echo ON
set CURRDIR=%CD%
set TEST_DIR=c:\tmp\salmon

powershell mkdir -ErrorAction SilentlyContinue %TEST_DIR%\test &^
npx http-server %TEST_DIR% -p 80 &^
cd %CURRDIR%