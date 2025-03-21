Salmon.Native  
version: 3.0.0
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
3. Tiny AES key expansion subroutine.  
To pull Tiny AES code change to the main branch and type:  
	```
	git submodule update --init --recursive --remote  
	```
5. Enable intrinsic functions /Oi  
  
Package:  
To package the app click on Build in Visual Studio.  
  
Packaging:  
To create the nuget package for SalmonNative from the command line
you need the Nuget executable. For instructions how to download it see:
https://learn.microsoft.com/en-us/nuget/consume-packages/install-use-packages-nuget-cli
And add the path to the PATH enviroment variable.
Then you can either build the project, the task should already exists as a post-build step in the vcxproj project file.
or you can package it manually:
```
NuGet.exe pack SalmonNative.nuspec -outputDir ..\..\..\..\output\nuget\releases  
```

  