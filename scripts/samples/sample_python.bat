set CURRDIR=%CD%
set SALMON_VERSION=2.3.0

pip install ..\..\output\python\salmon_core_py.%SALMON_VERSION%.tar.gz
pip install ..\..\output\python\salmon_fs_py.%SALMON_VERSION%.tar.gz
cd ..\..\samples\PythonSamples
python -O data.py text.py & ^
python -O data.py data.py & ^
python -O data.py data_stream.py & ^
python -O data.py file.py & ^
python -O data.py local_drive.py & ^
python -O data.py web_service_drive.py & ^
python -O data.py node_http_drive.py & ^
cd %CURRDIR%