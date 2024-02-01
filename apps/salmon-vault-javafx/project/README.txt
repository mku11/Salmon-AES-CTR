Salmon Vault - JavaFx
version: 1.0.5
project: https://github.com/mku11/Salmon-AES-CTR
license: MIT License https://github.com/mku11/Salmon-AES-CTR/blob/main/LICENSE

Run:
Make sure you have JavaFX installed on your machine you can download it from:
https://openjfx.io/
Set JAVAFX_HOME to the path you have installed it in your machine.
If you use windows you can set the variable in the start.bat script or start.sh for MacOS and linux.

Open source projects included:
TinyAES
project: https://github.com/kokke/tiny-AES-c
license: The Unlicense https://github.com/kokke/tiny-AES-c/blob/master/unlicense.txt

Java Native Access
project: https://github.com/java-native-access/jna
license: Apache-2.0 https://github.com/java-native-access/jna/blob/master/LICENSE

JavaFX
project: https://github.com/openjdk/jfx
license: GPLv2.0 https://github.com/openjdk/jfx/blob/master/LICENSE

Build
To build the app you will need:  
1. Intellij IDEA.
2. Gradle

Run the build task from gradle instead of the Intellij IDEA. This will include the native library.
Alternatively you can build from the command line:
gradlew.bat build -x test --rerun-tasks

To package the app build the artifacts from Intellij IDEA.