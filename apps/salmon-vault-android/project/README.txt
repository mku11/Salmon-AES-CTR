Salmon Vault - Android
version: 1.0.5
project: https://github.com/mku11/Salmon-AES-CTR
license: MIT License https://github.com/mku11/Salmon-AES-CTR/blob/main/LICENSE

Open source projects included:
TinyAES
project: https://github.com/kokke/tiny-AES-c
license: The Unlicense https://github.com/kokke/tiny-AES-c/blob/master/unlicense.txt

Build:
To build the app you will need:
1. Android Studio

Optional:  
If you want to include the fast AES intrinsics and Tiny AES:
uncomment line in app/build.gradle:
implementation 'com.mku.salmon:salmon-android-native:x.x.x'

Package:
From Android Studio menu bar click on Build / Generate Signed Bundle/APK
