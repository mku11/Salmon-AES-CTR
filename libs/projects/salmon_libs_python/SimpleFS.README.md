# SimpleFS for Salmon - Python
version: 1.0.2
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
python -m pip install packages/simple_fs.tar.gz  
```

To package:
```
dos2unix package.sh  
./package.sh  
```

To install:  
```
pip install packages/simple_fs.tar.gz  
```

To disable the type checks run with -O optimization  
  
If you use intellij IDEA and the package names are not resolved:
Add the simple_io and simple_fs folder paths to the interpreter paths under: 
File/Settings/Interpreters/Show All/Interpreter Paths
