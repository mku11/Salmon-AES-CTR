Make sure you build the war file via gradle and created all the artifacts in Intellij IDEA
If you need to run on HTTPS make sure you have generated a keystore file named keystore.p12
Edit file config/application.properties with your settings
Have a look at the Dockerfile to customize your image

To build the docker image:
docker build . -t salmon:salmon-ws

To run the app and expose it on host port 8181:
docker run -p 8181:8080 salmon:salmon-ws