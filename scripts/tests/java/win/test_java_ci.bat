@echo ON
set CURRDIR=%CD%

cd %CURRDIR%\..\..\..\misc &^
START start_http_server.bat &^
START start_ws_server.bat &^
TIMEOUT 10 &^
test_java_all.bat &^
cd %CURRDIR%
