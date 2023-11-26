Salmon.Native  
version: 1.0.6-SNAPSHOT  
project: https://github.com/mku11/Salmon-AES-CTR  
license: MIT License https://github.com/mku11/Salmon-AES-CTR/blob/main/LICENSE  
  
Open source projects included:  
TinyAES  
project: https://github.com/kokke/tiny-AES-c  
license: The Unlicense https://github.com/kokke/tiny-AES-c/blob/master/unlicense.txt  
  
Build  
To build the app you will need:  
1. Microsoft Visual Studio 2022  
2. Microsoft Visual Studio C++ installed (visual studio installer)  
3. Tiny AES key expansion subroutine. To pull Tiny AES code from the project root folder type:  
	git submodule update --init --recursive  
4. Make sure the project has these preprocessor definitions: USE_TINY_AES and AES256=1 symbols.  
5. Enable intrinsic functions /Oi  
  
Package:  
To package the app click on Build in Visual Studio.  
  
Packaging:  
To create the nuget package for SalmonNative from the command line:  
Make sure you have "Nuget command line package" installed from nuget.  
Navigate to the SalmonNative folder then type:  
..\packages\NuGet.CommandLine.6.7.0\tools\NuGet.exe pack SalmonNative.nuspec -outputDir ..\..\..\..\output\nuget\releases  
  
Alternatively you can add the command as a post build step in visual studio.  
  