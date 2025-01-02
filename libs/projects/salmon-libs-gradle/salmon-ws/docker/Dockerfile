FROM openjdk:latest
WORKDIR /opt/salmon
ADD salmon-ws-2.2.0.war . 
ADD keystore.p12 .
ADD start-salmon-ws.sh .
ADD config/application.properties config/application.properties
RUN mkdir -p fs
ENTRYPOINT ["/bin/sh","/opt/salmon/start-salmon-ws.sh"]
