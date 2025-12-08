set CURRDIR=%CD%
set SIMPLE_IO_VERSION=1.0.2
set SIMPLE_FS_VERSION=1.0.2
set SALMON_VERSION=3.0.3

cd ..\..\samples
python -m pip install .\libs\salmon\salmon-python\simple_io_py.%SIMPLE_IO_VERSION%.tar.gz & ^
python -m pip install .\libs\salmon\salmon-python\simple_fs_py.%SIMPLE_FS_VERSION%.tar.gz & ^
python -m pip install .\libs\salmon\salmon-python\salmon_core_py.%SALMON_VERSION%.tar.gz & ^
python -m pip install .\libs\salmon\salmon-python\salmon_fs_py.%SALMON_VERSION%.tar.gz & ^

cd PythonSamples & ^
python -O text.py & ^
python -O data.py & ^
python -O data_stream.py & ^
python -O file.py & ^
python -O local_drive.py & ^
python -O web_service_drive.py & ^
python -O http_drive.py & ^
cd %CURRDIR%