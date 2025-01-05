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

To enable GPU support for the native libary edit salmon-native/build.gradle and set enable_opencl to true.
For linux you need to update variables OPENCL_INCLUDE and OPENCL_LIB to the appropriate paths
```
project.ext.set('enable_opencl', true)
```

To build the java web service from the command line run:  
```
gradlew.bat bootWar -x test --rerun-tasks
```
Then build the artifacts from within IntelliJ IDEA

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

To start the web service:
start-salmon-ws.bat D:\path\to\rootfolder
Then type the user and password for basic auth

To connect to the web service using http (basic auth):
curl -X GET "http://localhost:8080/api/info?path=/" -u user:password

To connect to the web service using https:
uncomment the ssl properties in file application.properties then rebuild the war file
curl -X GET "https://localhost:8443/api/info?path=/" -u user:password --cert-type P12 --cert keystore.p12:'keypassword'
for developement/testing only use -k to bypass verification
curl -X GET "https://localhost:8443/api/info?path=/" -u user:password -k
