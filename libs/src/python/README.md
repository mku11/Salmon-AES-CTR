## Salmon for python

#### Requirements for python_core and python_fs
Python 3.11+  

#### Dependencies:
* python-interface  
* typeguard
* pycryptodome


install packages:  
python -m pip install python-interface
python -m pip install typeguard
python -m pip install pycryptodome

To run with AES intrinsics set the path to the dll:
NativeProxy.set_library_path("../../projects/salmon-libs-gradle/salmon-native/build/libs/salmon/shared/salmon.dll")
SalmonStream.set_aes_provider_type(ProviderType.AesIntrinsics)
