To build the JavaFX app you will need:  
1. Intellij IDEA ide  
2. Download the JavaFX SDK: https://openjfx.io/. Make sure you update the start.bat with the location of the JavaFX SDK   
3. If you want to use the AES intrinsics (optional) you will need:  
   a. Microsoft Visual Studio Compiler installed  
   b. Download tiny-aes: https://github.com/kokke/tiny-AES-c into folder c/src/tiny-aes  
   c. Modify aes.h and set: #define AES256 1  
   d. Edit file Config.java and set: enableNativeLib = true;  
   e. set VM option -Djava.library.path=SalmonNative/build/libs/salmon/shared in your run configuration in the IDE   