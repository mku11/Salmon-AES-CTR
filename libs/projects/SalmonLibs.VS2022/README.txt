To build the c# libraries you will need:  
1. Microsoft Visual Studio  
For additional requirements for each subproject see README.md in its respective folder.
2. If you want to build the AES intrinsics for Intel x86 (optional) you will need Microsoft Visual Studio C++ package (Visual Studio installer).

Build:
You can build from the windows command line:
msbuild SalmonDotNetLibs.sln

To debug the native code check the option under Project Properties/Debug/Debug Launch UI profiles/Enable native code debugging
Note that debugging the native code will probably disable the Edit and Continue for .NET code.

Packaging:  
The nuget packages for SalmonCore and SalmonFS will be created automatically during the build.  
To create the nuget package for SalmonNative see Salmon.Native/README.md file.  
