# SimpleIO for Salmon - Python
version: 1.0.2
project: https://github.com/mku11/Salmon-AES-CTR  
license: MIT License https://github.com/mku11/Salmon-AES-CTR/blob/main/LICENSE  

#### Requirements
Python 3.11+  

#### Dependencies: 
* typeguard
* wrapt
  
install dependencies:  
```
python -m pip install typeguard wrapt  
```

To package:
```
dos2unix package.sh  
./package.sh  
```

To install:  
```
pip install packages/simple-io.tar.gz  
```

To disable the type checks run with -O optimization  
  
If you use intellij IDEA and the package names are not resolved:  
Add simple_io folder path to the interpreter paths under: File/Settings/Interpreters/Show All/Interpreter Paths    
  