#!/bin/bash -x
CURRDIR=$(pwd)

# Unix format
echo Changing scripts format
find ../../ -name "*.sh" -not -path "*/node_modules/*" -not -path "*/deps/*" -exec dos2unix {} \;
find ../../ -name "gradlew" -not -path "*/node_modules/*" -not -path "*/deps/*" -exec dos2unix {} \;

dos2unix $CURRDIR/settings.cfg
source $CURRDIR/settings.cfg

echo Synchronizing project settings
echo SALMON_VERSION: $SALMON_VERSION
echo SALMON_APP_VERSION: $SALMON_APP_VERSION

# PROJECTS
echo
echo Syncing Projects
echo .NET
PATTERN="<SalmonVersion>[^/]*<\/SalmonVersion>"
SUBST="<SalmonVersion>$SALMON_VERSION<\/SalmonVersion>"

DIR=../../libs/projects/SalmonLibs.VS2022
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/Salmon.Core/Salmon.Core.csproj
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/Salmon.FS/Salmon.FS.csproj
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/Salmon.FS.Android/Salmon.FS.Android.csproj
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/Salmon.Native.Android/Salmon.Native.Android.csproj
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/Salmon.Native.Test/SalmonNativeTest.vcxproj
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/Salmon.Test/Salmon.Test.csproj
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/Salmon.Win/Salmon.Win.csproj
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/Salmon.Win.Test/Salmon.Win.Test.csproj
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/SalmonNative/SalmonNative.vcxproj
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/SalmonWinService/SalmonWinService.csproj
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/SalmonServiceTest/WinServiceTest.csproj

PATTERN="<version>[^/]*<\/version>"
SUBST="<version>$SALMON_VERSION<\/version>"
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/SalmonNative/SalmonNative.Debug.nuspec
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/SalmonNative/SalmonNative.DebugGPU.nuspec
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/SalmonNative/SalmonNative.Release.nuspec
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/SalmonNative/SalmonNative.ReleaseGPU.nuspec

echo
echo Java
PATTERN="^version '[^/]*'"
SUBST="version '$SALMON_VERSION'"
DIR=../../libs/projects/salmon-libs-gradle
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/build.gradle
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/salmon-core/build.gradle
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/salmon-fs/build.gradle
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/salmon-native/build.gradle
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/salmon-win/build.gradle

echo
echo Android
DIR=../../libs/projects/salmon-libs-gradle-android
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/build.gradle
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/salmon-fs-android/build.gradle
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/salmon-native-android/build.gradle

PATTERN="^SALMON_VERSION=[^/]*"
SUBST="SALMON_VERSION=$SALMON_VERSION"
DIR=../../libs/projects/salmon-libs-gcc
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/makefile

PATTERN="^version '[^/]*'"
SUBST="version '$SALMON_VERSION'"
DIR=../../libs/projects/salmon-libs-gradle-android/salmon-fs-android-test
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/build.gradle

PATTERN="^\tappVersion = '[^/]*'"
SUBST="\tappVersion = '$SALMON_APP_VERSION'"
DIR=../../libs/projects/salmon-libs-gradle-android/salmon-fs-android-test
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/build.gradle

PATTERN=".*versionName '[^/]*'"
SUBST="\t\tversionName '$SALMON_VERSION'"
DIR=../../libs/projects/salmon-libs-gradle-android/salmon-fs-android-test
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/build.gradle

PATTERN=".*versionCode [^/]*"
SUBST="\t\tversionCode $SALMON_APP_VERSION"
DIR=../../libs/projects/salmon-libs-gradle-android/salmon-fs-android-test
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/build.gradle


# SAMPLES
echo
echo Syncing Samples

PATTERN="^SALMON_VERSION=[^/]*"
SUBST="SALMON_VERSION=$SALMON_VERSION"
DIR=../../samples
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/get_salmon_libs.sh

PATTERN="^set SALMON_VERSION=[^/]*"
SUBST="set SALMON_VERSION=$SALMON_VERSION"
DIR=../../samples
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/get_salmon_libs.bat

PATTERN="<SalmonVersion>[^/]*<\/SalmonVersion>"
SUBST="<SalmonVersion>$SALMON_VERSION<\/SalmonVersion>"
DIR=../../samples/CSharpSamples/CSharpSamples
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/CSharpSamples.csproj

PATTERN="<SalmonVersion>[^/]*<\/SalmonVersion>"
SUBST="<SalmonVersion>$SALMON_VERSION<\/SalmonVersion>"
DIR=../../samples/CppSamples/CppSamples
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/CppSamples.vcxproj

PATTERN="<package id=\"Salmon.Native\" version=\"[^/]*\" targetFramework=\"native\" \/>"
SUBST="<package id=\"Salmon.Native\" version=\"$SALMON_VERSION\" targetFramework=\"native\" \/>"
DIR=../../samples/CppSamples/CppSamples
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/packages.config

PATTERN="^SALMON_VERSION=[^/]*"
SUBST="SALMON_VERSION=$SALMON_VERSION"
DIR=../../samples/JavascriptSamples
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/getdeps.sh

PATTERN="^set SALMON_VERSION=[^/]*"
SUBST="set SALMON_VERSION=$SALMON_VERSION"
DIR=../../samples/JavascriptSamples
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/getdeps.bat

PATTERN="^const SALMON_VERSION=[^/]*"
SUBST="const SALMON_VERSION=\"$SALMON_VERSION\";"
DIR=../../samples/JavascriptSamples
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/assets/js/node/node_common.js

PATTERN="^SALMON_VERSION=[^/]*"
SUBST="SALMON_VERSION=\"$SALMON_VERSION\""
DIR=../../samples/PythonSamples
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/common.py

PATTERN="^version '[^/]*'"
SUBST="version '$SALMON_VERSION'"
DIR=../../samples/JavaSamples
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/build.gradle

PATTERN="^version '[^/]*'"
SUBST="version '$SALMON_VERSION'"
DIR=../../samples/AndroidSamples/salmon-android-samples
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/build.gradle

PATTERN="^\sappVersion = '[^/]*'"
SUBST="\tappVersion = '$SALMON_APP_VERSION'"
DIR=../../samples/AndroidSamples/salmon-android-samples
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/build.gradle

PATTERN=".*versionName '[^/]*'"
SUBST="\t\tversionName '$SALMON_VERSION'"
DIR=../../samples/AndroidSamples/salmon-android-samples
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/build.gradle

PATTERN=".*versionCode [^/]*"
SUBST="\t\tversionCode $SALMON_APP_VERSION"
DIR=../../samples/AndroidSamples/salmon-android-samples
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/build.gradle

PATTERN="<SalmonVersion>[^/]*<\/SalmonVersion>"
SUBST="<SalmonVersion>$SALMON_VERSION<\/SalmonVersion>"
DIR=../../samples/DotNetAndroidSamples/DotNetAndroidSamples
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/DotNetAndroidSamples.csproj

PATTERN="<SalmonAppVersion>[^/]*<\/SalmonAppVersion>"
SUBST="<SalmonAppVersion>$SALMON_APP_VERSION<\/SalmonAppVersion>"
DIR=../../samples/DotNetAndroidSamples/DotNetAndroidSamples
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/DotNetAndroidSamples.csproj

PATTERN="^SALMON_VERSION=[^/]*"
SUBST="SALMON_VERSION=$SALMON_VERSION"
DIR=../../samples/GccSamples
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/makefile

# SAMPLES
echo
echo Syncing Samples Scripts
PATTERN="^set SALMON_VERSION=[^/]*"
SUBST="set SALMON_VERSION=$SALMON_VERSION"
DIR=../samples
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/sample_python.bat

PATTERN="^SALMON_VERSION=[^/]*"
SUBST="SALMON_VERSION=$SALMON_VERSION"
DIR=../samples
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/sample_python.sh

PATTERN="^SALMON_VERSION=[^/]*"
SUBST="SALMON_VERSION=$SALMON_VERSION"
DIR=../samples
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/sample_gcc_linux_x86_64.sh
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/sample_gcc_linux_aarch64.sh
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/sample_gcc_macos_x86_64.sh
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/sample_gcc_macos_aarch64.sh

# README
echo
echo Syncing README
PATTERN="^version:[^/]*"
SUBST="version: $SALMON_VERSION"

DIR=../../libs/projects/SalmonLibs.VS2022
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/Salmon.Core/README.md
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/Salmon.FS/README.md
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/Salmon.FS.Android/README.md
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/Salmon.Native.Android/README.md
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/Salmon.Win/README.md
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/SalmonNative/README.md
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/SalmonWinService/README.md

DIR=../../libs/projects/SalmonLibs.vscode/docs
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/Salmon.Core.README.txt
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/Salmon.FS.README.txt

DIR=../../libs/projects/salmon_libs_python
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/Salmon.Core.README.md
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/Salmon.FS.README.md

DIR=../../libs/projects/salmon-libs-gcc
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/README.txt

DIR=../../libs/projects/salmon-libs-xcode-macos
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/salmon/README.txt

PATTERN="^version = [^/]*"
SUBST="version = \"$SALMON_VERSION\""
FILE=../../libs/src/python/salmon_core/pyproject.toml
sed -i -e "s/$PATTERN/$SUBST/g" $FILE

FILE=../../libs/src/python/salmon_fs/pyproject.toml
sed -i -e "s/$PATTERN/$SUBST/g" $FILE

PATTERN="  'salmon_core == [^/]*',"
SUBST="  'salmon_core == $SALMON_VERSION',"
FILE=../../libs/src/python/salmon_fs/pyproject.toml
sed -i -e "s/$PATTERN/$SUBST/g" $FILE

# SCRIPTS
echo
echo Syncing Scripts
PATTERN="^set SALMON_VERSION=[^/]*"
SUBST="set SALMON_VERSION=$SALMON_VERSION"

PATTERN="^SALMON_VERSION=[^/]*"
SUBST="SALMON_VERSION=$SALMON_VERSION"

DIR=../../libs/projects/salmon_libs_python
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/package.sh

DIR=../../libs/projects/SalmonLibs.vscode
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/package.sh

DIR=../../libs/projects/salmon-libs-xcode-macos
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/salmon/package.sh

PATTERN="^set SALMON_VERSION=[^/]*"
SUBST="set SALMON_VERSION=$SALMON_VERSION"
DIR=./
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/start_ws_server.bat

PATTERN="^SALMON_VERSION=[^/]*"
SUBST="SALMON_VERSION=$SALMON_VERSION"
DIR=./
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/start_ws_server.sh

PATTERN="^SALMON_VERSION=[^/]*"
SUBST="SALMON_VERSION=$SALMON_VERSION"
DIR=../../libs/projects/salmon-libs-gradle
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/package.sh

PATTERN="^set SALMON_VERSION=[^/]*"
SUBST="set SALMON_VERSION=$SALMON_VERSION"
DIR=../../libs/projects/salmon-libs-gradle
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/package.bat