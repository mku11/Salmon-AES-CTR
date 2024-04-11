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
  
Configure the sdk location
Either create a file local.properties with the following:
sdk.dir=C\:\\Path\\To\\Android\\android-sdk
Or open Android Studio and configured in settings.

To build from the command line run:  
gradlew.bat build -x test --rerun-tasks    
  
To build the release aar libs which can be used for the VS project:  
gradlew.bat assembleRelease -x test --rerun-tasks  
  
To publish the maven aar libs to a local directory:  
gradlew.bat publish --rerun-tasks  

If you're in development and the snapshot dependencies have changed make sure you refresh:
gradlew.bat --refresh-dependencies
