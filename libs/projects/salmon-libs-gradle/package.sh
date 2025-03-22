#!/bin/bash
SALMON_VERSION=3.0.0
CURRDIR=$(pwd)

OUTPUT_ROOT=$CURRDIR/../../../output
NATIVE_OUTPUT_DIR=$OUTPUT_ROOT/native
SOURCE_ARCHIVE=./salmon-native/build/libs/salmon-native.zip

mkdir -p $NATIVE_OUTPUT_DIR

if [[ "$OSTYPE" == "linux-gnu"* ]]; then
	cp -f $SOURCE_ARCHIVE $NATIVE_OUTPUT_DIR/salmon-gradle-linux-x86_64.$SALMON_VERSION.zip
elif [[ "$OSTYPE" == "darwin"* ]]; then
	cp -f $SOURCE_ARCHIVE $NATIVE_OUTPUT_DIR/salmon-gradle-macos-x86_64.$SALMON_VERSION.zip
elif [[ "$OSTYPE" == "cygwin" ]]; then
	cp -f $SOURCE_ARCHIVE $NATIVE_OUTPUT_DIR/salmon-gradle-win-x86_64.$SALMON_VERSION.zip
fi

cd $CURRDIR