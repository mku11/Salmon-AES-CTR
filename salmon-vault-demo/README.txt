Salmon Vault - Javascript
version: 2.0.0
project: https://github.com/mku11/Salmon-AES-CTR
license: MIT License https://github.com/mku11/Salmon-AES-CTR/blob/main/LICENSE

Run:
You will need an http server to host the app or open the index.html locally with your browser (see below for limitations)

Web Browser Support:
For http-file based vaults Chrome, Firefox, Safari or any browser that supports modules ES2022
For local file based vaults only Chrome is supported.
For use with http-files without hosting on a server you can open the html file locally with your browser.
For use with local vaults drives without an http server you need to start chrome with these options:
--allow-file-access-from-files --disable-web-security

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

To Build:
To fetch and extract the salmon library dependencies run Terminal / Run Build Task.
or type in a command prompt:
getdeps.bat 

To package the app run:
./package.sh