## Salmon for python

#### Requirements for python_core and python_fs
Python 3.11+  

#### Dependencies:
* python-interface  
* typeguard
* pycryptodome
* wrapt

install dependencies:  
python -m pip install python-interface typeguard pycryptodome wrapt

to package:
./package_salmon_core.sh 
./package_salmon_fs.sh 

To install: 
pip install packages/salmon_core.tar.gz
pip install packages/salmon_fs.tar.gz

To disable the type checks run with -O optimization

To run with AES intrinsics set the path to the salmon native dll:
NativeProxy.set_library_path("../../projects/salmon-libs-gradle/salmon-native/build/libs/salmon/shared/salmon.dll")
SalmonStream.set_aes_provider_type(ProviderType.AesIntrinsics)
