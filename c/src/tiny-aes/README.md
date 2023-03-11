If you want to use the fast AES intrinsices for AMR64 (for Java Android) you need::
1. Tiny-AES key expansion , download tiny-aes: https://github.com/kokke/tiny-AES-c into folder ROOT/c/src/tiny-aes  
2. Modify aes.h and set: #define AES256 1
3. See the README file in Android app folder for any other steps.