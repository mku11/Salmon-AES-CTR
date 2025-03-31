Salmon native library for MacOS
version: 3.0.1
project: https://github.com/mku11/Salmon-AES-CTR  
license: MIT License https://github.com/mku11/Salmon-AES-CTR/blob/main/LICENSE  
  
To include in your library copy libsalmon.dylib to /usr/local/lib
Then add the headers to your xcode project.
Under Build Settings in xcode add:
Other Linker Flags: -lsalmon
Library Search Path: /the/path/to/salmonlib

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
