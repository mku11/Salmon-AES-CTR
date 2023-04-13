Download the Tiny-AES key expansion subroutine from https://github.com/kokke/tiny-AES-c  
Copy aes.c and aes.h into folder ROOT/c/src/tiny-aes  
Modify aes.h and uncomment: #define AES256 1  
Modify salmon.h and uncomment: #define USE_TINY_AES 1  