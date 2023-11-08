Salmon Vault - JavaFx
version: 1.0.5
project: https://github.com/mku11/Salmon-AES-CTR
license: MIT License https://github.com/mku11/Salmon-AES-CTR/blob/main/LICENSE

Open source projects inluded:
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