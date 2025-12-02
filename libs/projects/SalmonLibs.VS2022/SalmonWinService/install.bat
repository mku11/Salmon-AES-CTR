@ECHO OFF

set DOTNETFX2=%SystemRoot%\Microsoft.NET\Framework\v4.0.30319
set PATH=%PATH%;%DOTNETFX2%

sc.exe create SalmonWinService displayname="Salmon Cryptographic Service" binpath="%~dp0SalmonWinService.exe" start=auto
if %errorlevel%==0 (
	echo Salmon Service Installed
	net start SalmonWinService		
) else (
	echo Error has occured make sure you have enough privileges
	echo Right click and select "Run as admininstrator"
)
pause