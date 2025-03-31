set CURRDIR=%CD%
set SALMON_VERSION=3.0.1

powershell mkdir -ErrorAction SilentlyContinue d:\tmp\salmon
cd ..\..\output\java-ws\java-ws.%SALMON_VERSION%
start-salmon-ws.bat & ^
cd %CURRDIR%