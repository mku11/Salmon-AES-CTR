# Salmon.Core - Python
version: 3.0.1
project: https://github.com/mku11/Salmon-AES-CTR  
license: MIT License https://github.com/mku11/Salmon-AES-CTR/blob/main/LICENSE  

#### Requirements
Python 3.11+  

#### Dependencies: 
* typeguard
* pycryptodome
* wrapt
  
install dependencies:  
python -m pip install typeguard pycryptodome wrapt  
  
to run tests from command line:
cd libs\test\salmon_core_test_python\
python -m unittest -v salmon_core_tests.SalmonCoreTests.test_shouldEncryptAndDecryptText

to package:  
dos2unix package_salmon_core.sh  
./package_salmon_core.sh  
  
To install:  
pip install packages/salmon_core.tar.gz  
  
To disable the type checks run with -O optimization  
  
If you use intellij IDEA and the package names are not resolved make sure you add  
the salmon_core and salmon_fs folder path to the interpreter paths under File/Settings/Interpreters/Show All/Interpreter Paths  
  
To run with AES intrinsics set the path to the salmon native dll:  
NativeProxy.set_library_path("../../projects/salmon-libs-gradle/salmon-native/build/libs/salmon/shared/salmon.dll")  
AesStream.set_aes_provider_type(ProviderType.AesIntrinsics)  
