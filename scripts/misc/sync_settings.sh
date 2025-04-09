CURRDIR=$(pwd)
source $CURRDIR/settings.cfg

echo Synchronizing project settings
echo SALMON_VERSION: $SALMON_VERSION
echo SALMON_VERSION: $SALMON_APP_VERSION

# PROJECTS
PATTERN="<SalmonVersion>[^/]*<\/SalmonVersion>"
SUBST="<SalmonVersion>$SALMON_VERSION<\/SalmonVersion>"

FILE=../../services/winservice/project/SalmonServiceTest/WinServiceTest.csproj
sed -i -e "s/$PATTERN/$SUBST/g" $FILE
FILE=../../services/winservice/project/SalmonWinService/SalmonWinService.csproj
sed -i -e "s/$PATTERN/$SUBST/g" $FILE

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

PATTERN="<version>[^/]*<\/version>"
SUBST="<version>$SALMON_VERSION<\/version>"
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/SalmonNative/SalmonNative.nuspec

PATTERN="^version '[^/]*'"
SUBST="version '$SALMON_VERSION'"
DIR=../../libs/projects/salmon-libs-gradle
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/build.gradle
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/salmon-core/build.gradle
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/salmon-fs/build.gradle
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/salmon-native/build.gradle
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/salmon-win/build.gradle

DIR=../../libs/projects/salmon-libs-gradle-android
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/build.gradle
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/salmon-fs-android/build.gradle
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/salmon-native-android/build.gradle

DIR=../../services/webservice/project
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/salmon-ws/build.gradle

PATTERN="^SALMON_VERSION=[^/]*"
SUBST="SALMON_VERSION=$SALMON_VERSION"
DIR=../../libs/projects/salmon-libs-gcc
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/makefile

# SAMPLES

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
DIR=../samples
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/sample_gcc_win_x86_64.sh
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/sample_gcc_linux_x86_64.sh
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/sample_gcc_linux_aarch64.sh
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/sample_gcc_macos_x86_64.sh

PATTERN="^SALMON_VERSION=[^/]*"
SUBST="SALMON_VERSION=$SALMON_VERSION"
DIR=../../samples/JavascriptSamples
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/getdeps.sh

PATTERN="^set SALMON_VERSION=[^/]*"
SUBST="set SALMON_VERSION=$SALMON_VERSION"
DIR=../../samples/JavascriptSamples
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/getdeps.bat

PATTERN="^set SALMON_VERSION=[^/]*"
SUBST="set SALMON_VERSION=$SALMON_VERSION"
DIR=../samples
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/sample_python.bat

PATTERN="^SALMON_VERSION=[^/]*"
SUBST="SALMON_VERSION=$SALMON_VERSION"
DIR=../samples
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/sample_python.sh

PATTERN="^version '[^/]*'"
SUBST="version '$SALMON_VERSION'"
DIR=../../samples/JavaSamples
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/build.gradle

PATTERN="^version '[^/]*'"
SUBST="version '$SALMON_VERSION'"
DIR=../../samples/AndroidSamples/salmon-android-samples
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/build.gradle

PATTERN="^\sappVersion '[^/]*'"
SUBST="\tappVersion '$SALMON_APP_VERSION'"
DIR=../../samples/AndroidSamples/salmon-android-samples
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/build.gradle

# README
PATTERN="^version:[^/]*"
SUBST="version: $SALMON_VERSION"

DIR=../../libs/projects/SalmonLibs.VS2022
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/Salmon.Core/README.md
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/Salmon.FS/README.md
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/Salmon.FS.Android/README.md
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/Salmon.Native.Android/README.md
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/Salmon.Win/README.md
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/SalmonNative/README.md

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

DIR=../../services/webservice/project
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/salmon-ws/README.txt

FILE=../../services/winservice/project/SalmonWinService/README.txt
sed -i -e "s/$PATTERN/$SUBST/g" $FILE

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
PATTERN="^set SALMON_VERSION=[^/]*"
SUBST="set SALMON_VERSION=$SALMON_VERSION"

DIR=../../libs/projects/SalmonLibs.vscode
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/package.bat

PATTERN="^SALMON_VERSION=[^/]*"
SUBST="SALMON_VERSION=$SALMON_VERSION"

DIR=../../libs/projects/salmon_libs_python
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/package.sh

DIR=../../libs/projects/SalmonLibs.vscode
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/package.sh

DIR=../../libs/projects/salmon-libs-xcode-macos
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/salmon/package.sh

PATTERN="^SALMON_VERSION=[^/]*"
SUBST="SALMON_VERSION=$SALMON_VERSION"
DIR=../../services/webservice/project
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/salmon-ws/package.sh

PATTERN="^set SALMON_VERSION=[^/]*"
SUBST="set SALMON_VERSION=$SALMON_VERSION"
DIR=../../services/webservice/project
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/salmon-ws/package.bat

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