To build the c# libraries you will need:  
1. Microsoft Visual Studio  
For additional requirements for each subproject see README.md in its respective folder.
2. If you want to build the AES intrinsics for Intel x86 (optional) you will need Microsoft Visual Studio C++ package (Visual Studio installer).
3. To build the android native library you have to build first the gradle project under: libs/projects/salmon-libs-gradle-android/. Make sure you folow the instructions in the README.md file to build the release version of the library.

Restore nuget packages:
msbuild -t:restore 

Build:
You can build from the windows command line in windows:
msbuild /p:Configuration=Release

In linux you can build only the C# projects
If you need to build the native libraries in linux use salmon-libs-gradle or salmon-libs-gcc
sudo apt-get install -y dotnet-runtime-7.0
dotnet workload install wasm-tools-net7
dotnet workload restore
dotnet msbuild -t:restore
dotnet msbuild Salmon.Core

To clean:
msbuild -t:clean

Test:
To test the native libraries you will need Tiny Aes
To download Tiny Aes source code from the project root folder type:
git submodule update --recursive --init

To test C# library from the command line in windows:
vstest.console Salmon.Test\bin\Debug\net7.0-windows\Salmon.Test.dll /Tests:ShouldEncryptAndDecryptTextCompatible /Logger:Console;verbosity=detailed
To test the native C library from the command line:
vstest.console x64\Debug\Salmon.Native.Test.dll /Tests:TestExamples /Logger:Console;verbosity=detailed

In linux you can run tests using the dotnet tool:
dotnet vstest Salmon.Test/bin/Debug/net7.0-windows/Salmon.Test.dll /Tests:ShouldEncryptAndDecryptTextCompatible /Logger:Console;verbosity=detailed

To debug the native code check the option under Project Properties/Debug/Debug Launch UI profiles/Enable native code debugging
Note that debugging the native code will probably disable the Edit and Continue for .NET code.

Packaging:  
The nuget packages for SalmonCore and SalmonFS will be created automatically during the build.  
To create the nuget package for SalmonNative see Salmon.Native/README.md file.  
Make sure you're under release configuration before building.