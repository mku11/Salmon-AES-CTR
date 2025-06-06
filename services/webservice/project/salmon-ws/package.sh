#!/bin/bash
SALMON_VERSION=3.0.1

CURRDIR=$(pwd)
JAVA_WS=java-ws
WS_APP_PROPERTIES=./config/application.properties
WS_SCRIPT_SH=./scripts/start-salmon-ws.sh
WS_SCRIPT_BAT=./scripts/start-salmon-ws.bat
WS_WAR=./build/libs/salmon-ws-$SALMON_VERSION.war
WS_WAR_NAME=salmon-ws.war

CONFIG_DIR=config
OUTPUT_ROOT=../../../../output
WS_OUTPUT_DIR=$OUTPUT_ROOT/$JAVA_WS
WS_PACKAGE_NAME=$JAVA_WS.$SALMON_VERSION
WS_PACKAGE_NAME_ZIP=$JAVA_WS.$SALMON_VERSION.zip

mkdir -p $WS_OUTPUT_DIR

# Web Service
rm -rf $WS_OUTPUT_DIR/$WS_PACKAGE_NAME
mkdir -p $WS_OUTPUT_DIR/$WS_PACKAGE_NAME
mkdir -p $WS_OUTPUT_DIR/$WS_PACKAGE_NAME/$CONFIG_DIR
cp $WS_SCRIPT_BAT $WS_OUTPUT_DIR/$WS_PACKAGE_NAME
cp $WS_SCRIPT_SH $WS_OUTPUT_DIR/$WS_PACKAGE_NAME
cp $WS_WAR $WS_OUTPUT_DIR/$WS_PACKAGE_NAME/$WS_WAR_NAME
cp $WS_APP_PROPERTIES $WS_OUTPUT_DIR/$WS_PACKAGE_NAME/$CONFIG_DIR
cp README.txt $WS_OUTPUT_DIR/$WS_PACKAGE_NAME/README.txt
cd $WS_OUTPUT_DIR/$WS_PACKAGE_NAME
zip -r ../$WS_PACKAGE_NAME.zip *
cd $CURRDIR