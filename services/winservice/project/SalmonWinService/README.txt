SalmonWinService
version: 2.0.0 
project: https://github.com/mku11/Salmon-AES-CTR  
license: MIT License https://github.com/mku11/Salmon-AES-CTR/blob/main/LICENSE  
  
Build:  
To build the app you will need:  
1. Microsoft Visual Studio 2022  

You can build from the windows command line:
msbuild /p:Configuration=Release

Restore nuget packages:
msbuild -t:restore 

To clean:
msbuild -t:clean

To deploy:
To package select Build/Publish from within Visual Studio or run on the command line:
msbuild /t:publish /p:PublishProfile=Properties\PublishProfiles\FolderProfile.pubxml /p:Configuration=Release

Install:
To install the service in Windows right click on install.bat and choose Run as administrator.
To uninstall the service in Windows right click on uninstall.bat and choose Run as administrator.

To start the service from the command line:
net start SalmonWinService

To stop the service from the command line:
sc.exe stop SalmonWinService
