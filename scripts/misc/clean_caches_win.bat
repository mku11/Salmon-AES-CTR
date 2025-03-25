:: delete maven caches
rd /s /q %USERPROFILE%\.m2\salmon

:: delete gradle caches
rd /s /q %USERPROFILE%\.gradle\caches\modules-2\files-2.1\com.mku.salmon

:: delete nuget caches
rd /s /q %USERPROFILE%\.nuget\packages\salmon.core
rd /s /q %USERPROFILE%\.nuget\packages\salmon.fs
rd /s /q %USERPROFILE%\.nuget\packages\salmon.fs.android
rd /s /q %USERPROFILE%\.nuget\packages\salmon.native
rd /s /q %USERPROFILE%\.nuget\packages\salmon.native.android
rd /s /q %USERPROFILE%\.nuget\packages\salmon.win