To build the Salmon FS libraries for Android you need:  
1. Android Studio  
2. Intellij IDEA.  
  
Optional native libraries:  
The android native include fast Salmon AES-NI intrinsics and TinyAES for x86 and ARM.  
To build the native libraries you will need TinyAES 
To download Tiny Aes source code from the project root folder type:
git submodule update --recursive --init

  
Requirements for native libraries:  
1. Tiny-AES for more details on how to download see ROOT/c/src/README.md  
2. Edit file build.gradle and uncomment line: path "../../make/CMakeLists.txt"      
3. Android NDK you can download from within Android studio.    
  
To build from the command line run:  
./gradlew build -x test --rerun-tasks    
  
To build the release aar libs:  
./gradlew assembleRelease -x test --rerun-tasks  
  
To publish the maven aar libs to a local directory:  
./gradlew publish --rerun-tasks  
