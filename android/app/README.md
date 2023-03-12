To build the Salmon Vault Android app you will need:  
1. Android Studio  
  
Optional:  
If you want to include the fast AES intrinsics for ARM64 you will need:
1. The Tiny-AES key expansion subroutine, for more details see ROOT/c/src/tiny-aes/README.md
2. Edit file build.gradle and uncomment line: path "../../make/CMakeLists.txt"  
3. Edit file Config.java and set: enableNativeLib = true;  
4. Android NDK you can download from within Android studio.  