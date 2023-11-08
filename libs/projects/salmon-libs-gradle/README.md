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
- BouncyCastle for Java: bcprov-jdk15on:1.70  
- Microsoft VC Compiler (needed for the salmon-native library). Alternatively use cygwin GCC or MinGW.  
Note: If you don't want to build the native library just remove the subproject from gradle.
Salmon will use the Java default cipher for AES256.   
  
To build the java and native libraries from the command line run:  
```
./gradlew build -x test --rerun-tasks
```

To create the maven packages (see root folder output dir):  
```
./gradlew publish --rerun-tasks  
```

## Tests and Benchmarks:  
test files for core, fs, and native subprojects are included in respective test sourceSets (See build.gradle).  
see salmon-core/src/jmh folder for benchmarks. To run from the command line:  

```
./gradlew :salmon-core:jmh --rerun-tasks  
```