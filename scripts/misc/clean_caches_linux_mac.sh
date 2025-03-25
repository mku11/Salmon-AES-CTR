# delete maven caches
rm -rf $HOME/.m2/salmon

# delete gradle caches
rm -rf $HOME/.gradle/caches/modules-2/files-2.1/com.mku.salmon

# delete nuget caches
rm -rf $HOME/.nuget/packages/salmon.core
rm -rf $HOME/.nuget/packages/salmon.fs
rm -rf $HOME/.nuget/packages/salmon.fs.android
rm -rf $HOME/.nuget/packages/salmon.native
rm -rf $HOME/.nuget/packages/salmon.native.android
rm -rf $HOME/.nuget/packages/salmon.win