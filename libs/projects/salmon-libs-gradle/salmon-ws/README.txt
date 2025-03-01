SalmonWinService
version: 2.3.0
project: https://github.com/mku11/Salmon-AES-CTR  
license: MIT License https://github.com/mku11/Salmon-AES-CTR/blob/main/LICENSE  
  
Build:  
To build the app you will need gradle

You can build from the command line:
gradlew.bat build

To package:
run package.sh in a shell

To start the web service:
start-salmon-ws.bat
Then type the user and password for basic auth

To connect to the web service using http (basic auth):
curl -X GET "http://localhost:8080/api/info?path=/" -u user:password

To connect to the web service using https:
uncomment the ssl properties in file application.properties then rebuild the war file
curl -X GET "https://localhost:8443/api/info?path=/" -u user:password --cert-type P12 --cert keystore.p12:'keypassword'
for developement/testing only use -k to bypass verification
curl -X GET "https://localhost:8443/api/info?path=/" -u user:password -k