set CURRDIR=%CD%

powershell mkdir -ErrorAction SilentlyContinue d:\tmp\salmon
cd d:\tmp\salmon
pip install rangehttpserver & ^
python -m RangeHTTPServer --bind 127.0.0.1 & ^
cd %CURRDIR%