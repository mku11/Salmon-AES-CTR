Python Samples

Requirements:
Python 3.11+  

Dependencies:
From the command line run:
cd samples
get_salmon_libs.bat
cd samples/libs/salmon/salmon-python
pip install simple_io_py.tar.gz
pip install simple_fs_py.tar.gz
pip install salmon_core_py.tar.gz
pip install salmon_fs_py.tar.gz

when running make sure you disable type checks with -O option for better performance
python -O data.py