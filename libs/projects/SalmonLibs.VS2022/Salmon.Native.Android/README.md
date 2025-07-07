Salmon.Native.Android  
version: 3.0.2
project: https://github.com/mku11/Salmon-AES-CTR  
license: MIT License https://github.com/mku11/Salmon-AES-CTR/blob/main/LICENSE  
  
Build  
To build the native android libraries you will need:  
1. Microsoft Visual Studio 2022  
2. .Net Multi-platform App UI development (part of the Visual Studio installer)  
3. Gradle  
  
To build the native libraries you will need TinyAES 
To download Tiny Aes source code from the project root folder type:
git submodule update --recursive --init
  
Package:  
To package the app click on Build in Visual Studio the native libraries will be automatically linked and included in the package.  
  