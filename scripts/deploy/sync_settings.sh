CURRDIR=$(dirname "$0")
source $CURRDIR/settings.cfg

echo Synchronizing project settings
echo SALMON_VERSION: $SALMON_VERSION

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
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/salmon-ws/build.gradle

DIR=../../libs/projects/salmon-libs-gradle-android
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/build.gradle
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/salmon-fs-android/build.gradle
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/salmon-native-android/build.gradle

PATTERN="^SALMON_VERSION=[^/]*"
SUBST="SALMON_VERSION=$SALMON_VERSION"
DIR=../../libs/projects/salmon-libs-gcc
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/makefile

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

DIR=../../libs/projects/salmon-libs-gradle
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/salmon-ws/README.txt

FILE=../../services/winservice/project/SalmonWinService/README.txt
sed -i -e "s/$PATTERN/$SUBST/g" $FILE

# SCRIPTS

PATTERN="^SALMON_VERSION=[^/]*"
SUBST="SALMON_VERSION=$SALMON_VERSION"

DIR=../../libs/projects/salmon_libs_python
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/package.sh

DIR=../../libs/projects/SalmonLibs.vscode
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/package.sh

DIR=../../libs/projects/salmon-libs-xcode-macos
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/salmon/package.sh

DIR=../../libs/projects/salmon-libs-gradle
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/salmon-ws/package.sh
sed -i -e "s/$PATTERN/$SUBST/g" $DIR/salmon-ws/package.bat
