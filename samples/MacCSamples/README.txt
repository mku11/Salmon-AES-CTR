To compile Sample you will need:
xcode

download salmon-macos-x86_64.dmg and mount it
create a folder salmon-lib under the MacCSamples folder
copy the contents of the dmg to salmon-lib
you should now have this folder structure:

MacCSamples.xcodeproj
README.txt
MacCSamples
-main.c
-salmon-lib/
--salmon/include/
--salmon-jni/include/
--lib/

To build and run use xcode
make sure you update DYLD_LIBRARY_PATH and set the path to libsalmon.dylib
or copy it to /usr/local/lib
