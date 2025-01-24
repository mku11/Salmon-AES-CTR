To compile the C++ sample for Windows you will need:
Visual Studio 2022

All dependencies will be downloaded in packages folder when you restore

To build:
Open sln file in Visual Studio
Right click on solution and choose Restore nuget packages
Build and run

Or from command line:
msbuild -p:RestorePackagesConfig=true -t:restore
msbuild

