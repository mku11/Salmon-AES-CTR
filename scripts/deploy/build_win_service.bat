set CURRDIR=%CD%

:: to run type VsDevCmd.bat in the windows console before running this script

cd ..\..\services\winservice\project
msbuild -t:clean & ^
msbuild -t:restore & ^
msbuild /property:Configuration=Release & ^
msbuild /t:publish /p:PublishProfile=Properties\PublishProfiles\FolderProfile.pubxml /p:Configuration=Release & ^
cd %CURRDIR%