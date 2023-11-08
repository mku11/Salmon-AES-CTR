## Salmon Windows Libraries for Java:    

A windows tamper-proof secure file-based nonce sequencer.  
A windows client for communicating with the protected sequencer provided by the Salmon Windows Service.  
  
### Gradle:  

You can add the libraries to your project using gradle:  
```
repositories {
    maven {
        allowInsecureProtocol true
        url 'http://localhost/repository/maven/releases'
    }
    ...
}
dependencies {
	implementation 'com.mku.salmon:salmon-core:1.0.5'
    implementation 'com.mku.salmon:salmon-fs:1.0.5'
    implementation 'com.mku.salmon:salmon-win:1.0.5'
}
```

To build the Salmon Windows libraries you need:    
- JDK 11 liberica distribution is recommended.   
  
Dependencies:  
- JNA library  
  
Test dependencies:  
- BouncyCastle  
  
To build the java and native libraries from the command line run:  
./gradlew build -x test --rerun-tasks  
  
You can add the libraries to your project from the build folders or download the binaries from github.  
   
To create a maven package:  
./gradlew publish --rerun-tasks  
