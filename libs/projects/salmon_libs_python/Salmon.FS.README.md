# Salmon.FS - Python
version: 3.0.3
project: https://github.com/mku11/Salmon-AES-CTR  
license: MIT License https://github.com/mku11/Salmon-AES-CTR/blob/main/LICENSE  
  
#### Requirements
Python 3.11+  
  
#### Dependencies:
* typeguard
* pycryptodome
* wrapt
* simple_io
* simple_fs

install dependencies:  
```
python -m pip install typeguard pycryptodome wrapt  
```

install local dependencies:  
```
python -m pip install packages/salmon_core.tar.gz  
python -m pip install packages/simple_io.tar.gz  
python -m pip install packages/simple_fs.tar.gz  
```

to run tests from command line:
```
cd libs\test\salmon_fs_test_python\
python -m unittest -v salmon_fs_tests.SalmonFSTests.test_AuthorizedPositive
```

To package:
```
dos2unix package.sh  
./package.sh  
```

To install:  
```
pip install packages/salmon_fs.tar.gz  
```

To disable the type checks run with -O optimization  
  
If you use intellij IDEA and the package names are not resolved make sure you add    
Add simple_io, simple_fs, salmon_core, and salmon_fs folder paths to the interpreter paths under: File/Settings/Interpreters/Show All/Interpreter Paths    
  
To run with AES intrinsics set the path to the salmon native dll:
```
NativeProxy.set_library_path("../../projects/salmon-libs-gradle/salmon-native/build/libs/salmon/shared/salmon.dll")    
AesStream.set_aes_provider_type(ProviderType.AesIntrinsics)
```  
