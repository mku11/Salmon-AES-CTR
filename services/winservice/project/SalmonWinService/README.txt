SalmonWinService
version: 1.0.5  
project: https://github.com/mku11/Salmon-AES-CTR  
license: MIT License https://github.com/mku11/Salmon-AES-CTR/blob/main/LICENSE  
  
Install:
To install the service in Windows right click on install.bat and choose Run as administrator.

To uninstall the service in Windows right click on uninstall.bat and choose Run as administrator.

Build:  
To build the app you will need:  
1. Microsoft Visual Studio 2022  

To start the service:
net start SalmonWinService

To stop the service:
sc.exe stop SalmonWinService

Package:
To package select Build/Publish from within Visual Studio.