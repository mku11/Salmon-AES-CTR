CURRDIR=$(pwd)
SALMON_VERSION=3.0.1

pip install ../../output/python/salmon_core_py.$SALMON_VERSION.tar.gz
pip install ../../output/python/salmon_fs_py.$SALMON_VERSION.tar.gz
cd ../../samples/PythonSamples
python -O text.py
python -O data.py
python -O data_stream.py
python -O file.py
python -O local_drive.py
python -O web_service_drive.py
python -O http_drive.py
cd $CURRDIR