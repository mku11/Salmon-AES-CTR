Salmon native library for MacOS
version: 2.1.0  
project: https://github.com/mku11/Salmon-AES-CTR  
license: MIT License https://github.com/mku11/Salmon-AES-CTR/blob/main/LICENSE  
  
Open source projects included:  
TinyAES  
project: https://github.com/kokke/tiny-AES-c  
license: The Unlicense https://github.com/kokke/tiny-AES-c/blob/master/unlicense.txt  
  
To build Salmon for MacOS you will need:
xcode

Open salmon.xcodeproj file with xcode
Click on File / Project Properties
Choose Derived Data: Project-relative Location
Click Done
From the menu click Product / Scheme / Edit Scheme
Select Run
Select Info from the tabs and choose Build Configuration: Release
Close
From the menu Click Product / Build

To package run on the terminal:
./package.sh
