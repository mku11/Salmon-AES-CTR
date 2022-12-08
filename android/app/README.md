To build the Android app you will need:  
1. Android Studio ide  
2. If you want to use the AES intrinsics for ARM (optional) you will need:  
   a. Download tiny-aes: https://github.com/kokke/tiny-AES-c into folder c/src/tiny-aes  
   b. Modify aes.h and set: #define AES256 1  
   c. Edit file build.gradle and uncomment line: path "../../make/CMakeLists.txt"
   d. Edit file Config.java and set: enableNativeLib = true;