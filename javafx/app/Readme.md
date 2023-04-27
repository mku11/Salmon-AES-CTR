To build the JavaFX app you will need:  
1. Intellij IDEA ide with Java 11 (liberica JDK should work fine)
2. Download the JavaFX SDK: https://openjfx.io/. Make sure you update the start.bat with the location of the JavaFX SDK   
Optional:  
If you want to use the fast AES intrinsics for x86 you will need:  
1. Microsoft Visual Studio Compiler installed  
2. Edit file Config.java and set: enableNativeLib = true;  
3. Include the path to salmon.dll by setting VM option -Djava.library.path=SalmonNative/build/libs/salmon/shared in your run configuration in the IDE   
4. When deploying include salmon.dll file into the same directory as the deployment jar.  
  
Optional:  
If you want to use the alternative TinyAES for x86 you will need:  
1. Tiny-AES, for more details see ROOT/c/src/tiny-aes/README.md  
2. Follow the same instructions as above (AES intrinsics)  
