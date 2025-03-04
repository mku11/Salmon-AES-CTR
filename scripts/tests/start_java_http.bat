set CURRDIR=%CD%

powershell mkdir -ErrorAction SilentlyContinue d:\tmp\salmon
cd d:\tmp\salmon
pip install rangehttpserver
python -m RangeHTTPServer
cd %CURRDIR%