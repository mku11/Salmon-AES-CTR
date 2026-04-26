@echo ON
set CURRDIR=%CD%
set TEST_DIR=c:\tmp\salmon

powershell mkdir -ErrorAction SilentlyContinue %TEST_DIR%\test &^
cd %CURRDIR%\..\..\libs\deps\WebFS\output\webfs-service\webfs-service &^
start-webfs-service.bat &^
cd %CURRDIR%