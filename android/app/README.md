To build the Salmon Vault Android app you will need:  
1. Android Studio  
  
Optional:  
If you want to include the fast AES intrinsics for ARM64 you will need:  
1. The Tiny-AES key expansion subroutine, download source code from https://github.com/kokke/tiny-AES-c into folder ROOT/c/src/tiny-aes  
2. Modify aes.h and set: #define AES256 1  
3. Edit file build.gradle and uncomment line: path "../../make/CMakeLists.txt"  
4. Edit file Config.java and set: enableNativeLib = true;  
5. Android NDK you can download from within Android studio.