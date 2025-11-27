## Salmon Libraries for Java:    
  
salmon-core:    
Salmon core streams, encryptors and decryptors, secure random generators, and password key derivation.     
  
salmon-fs:  
Salmon drive, virtual filesystem API, input file streams w/ parallelism.    
  
salmon-native:  
Salmon AES intrinsics for 256bit keys.    
TinyAES implementation. This is needed if your processor/arch does not support AES intrinsics and for ARM key expansion.

## Development
  
### To build the Salmon Libraries in jar format you need:  
- JDK 11 liberica distribution is recommended.   
- Microsoft VC Compiler (needed for the salmon-native library). Alternatively use cygwin GCC or MinGW.  
Note: If you don't want to build the native library just remove the subproject from gradle.
Salmon will use the Java default cipher for AES256.   

To build the java and native libraries from the command line run:  
```
gradlew.bat :salmon-native:build --rerun-tasks
gradlew.bat build -x test --rerun-tasks
```

To enable GPU support for the native libary you need to set the following environment variable:
ENABLE_GPU=true
And install OpenCL SDK
for Windows:
```
cd scripts\misc
get_opencl.bat
```

For Linux:
```
sudo apt install -y ocl-icd-opencl-dev
```


You also need to specify the paths using variables OPENCL_INCLUDE and OPENCL_LIB to the appropriate paths. These variables can be found in build.gradle where you can also edit them directly or via the command line, see the scripts in scripts/deploy directory.

To create the maven packages (see root folder output dir):  
```
gradlew.bat publish -x test --rerun-tasks  
```

## Tests and Benchmarks:  
test files for core, fs, and native subprojects are included in respective test sourceSets (See build.gradle).  
To run a specific test case:
```
gradlew.bat :salmon-fs:test --tests "com.mku.salmon.test.SalmonFSTests.shouldAuthorizePositive" --rerun-tasks -i   
```

To include GPU tests in performance tests:
```
gradlew.bat :salmon-core:test --rerun-tasks -i -DENABLE_GPU=true
```

To use a different specific temporary directory for testing use:
```
gradlew.bat :salmon-fs:test -DtestDir="D:\tmp\salmon\test"
```

see salmon-core/src/jmh folder for benchmarks. To run from the command line:  

```
gradlew.bat :salmon-core:jmh --rerun-tasks  
```

To start the WebFS service:
```
start-webfs-service.bat
```
for more info see WebFS/README.md
