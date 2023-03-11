To build the JavaFX app you will need:  
1. Intellij IDEA ide with Java 11 (liberica JDK should work fine)
2. Download the JavaFX SDK: https://openjfx.io/. Make sure you update the start.bat with the location of the JavaFX SDK   

Optional:  
If you want to use the fast AES intrinsics for x86 you will need:  
1. Microsoft Visual Studio Compiler installed  
4. Edit file Config.java and set: enableNativeLib = true;  
5. Include the path to salmon.so by setting VM option -Djava.library.path=SalmonNative/build/libs/salmon/shared in your run configuration in the IDE   
6. When deploying include salmon.so file into the same directory as the deployment jar.  