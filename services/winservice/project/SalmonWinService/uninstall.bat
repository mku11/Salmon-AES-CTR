@ECHO OFF

set DOTNETFX2=%SystemRoot%\Microsoft.NET\Framework\v4.0.30319
set PATH=%PATH%;%DOTNETFX2%

sc.exe stop SalmonWinService
sc.exe delete SalmonWinService
if %errorlevel%==0 (
	echo Salmon Service Uninstalled
) else (
	echo Error has occured make sure you have enough privileges
	echo Right click and select "Run as admininstrator"
)
pause