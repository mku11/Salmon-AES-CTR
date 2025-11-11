# Salmon.Core - Python
version: 3.0.2
project: https://github.com/mku11/Salmon-AES-CTR  
license: MIT License https://github.com/mku11/Salmon-AES-CTR/blob/main/LICENSE  

#### Requirements
Python 3.11+  

#### Dependencies: 
* typeguard
* pycryptodome
* wrapt
* simple_io
  
install dependencies:  
```
python -m pip install typeguard pycryptodome wrapt  
```

install local dependencies:  
```
python -m pip install packages/simple_io.tar.gz  
```

to run tests from command line:
```
cd libs\test\salmon_core_test_python\
python -m unittest -v salmon_core_tests.SalmonCoreTests.test_shouldEncryptAndDecryptText
```

To package:
```
dos2unix package.sh  
./package.sh  
```

To install:  
```
pip install packages/salmon_core.tar.gz  
```

To disable the type checks run with -O optimization  
  
If you use intellij IDEA and the package names are not resolved:
```
Add simple_io and salmon_core folder paths to the interpreter paths under: File/Settings/Interpreters/Show All/Interpreter Paths  
```

To run with AES intrinsics set the path to the salmon native dll:  
```
NativeProxy.set_library_path("../../projects/salmon-libs-gradle/salmon-native/build/libs/salmon/shared/salmon.dll")  
AesStream.set_aes_provider_type(ProviderType.AesIntrinsics)
```  
