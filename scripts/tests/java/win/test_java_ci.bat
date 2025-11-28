@echo ON
set CURRDIR=%CD%

cd %CURRDIR%\..\..\..\misc &^
START start_http_server.bat &^
START start_ws_server.bat &^
TIMEOUT 10 &^
cd %CURRDIR%\..\..\..\..\libs\projects\salmon-libs-gradle &^
gradlew.bat test -x :salmon-win:test -DENC_THREADS=1 -DAES_PROVIDER_TYPE=Default -i -DTEST_MODE=WebService &^
cd %CURRDIR%
