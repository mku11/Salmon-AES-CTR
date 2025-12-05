@echo ON
set CURRDIR=%CD%
set TEST_DIR=c:\tmp\salmon

set WEBFS_VERSION=1.0.0

powershell mkdir -ErrorAction SilentlyContinue %TEST_DIR%\test &^
cd %CURRDIR%\..\..\libs\deps\WebFS\output\webfs-service\webfs-service-%WEBFS_VERSION% &^
start-webfs-service.bat &^
cd %CURRDIR%