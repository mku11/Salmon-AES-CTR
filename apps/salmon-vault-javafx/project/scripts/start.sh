#!/bin/bash
# uncomment and enter the javafx library path
# export JAVAFX_HOME="/path/to/javafx-sdk-17.0.10"

export SALMON_PATH="./*"
export JAVAFX_MODULES="--add-modules=javafx.controls --add-modules=javafx.swing --add-modules=javafx.fxml --add-modules=javafx.media --add-modules=javafx.graphics"

if [ -d "$JAVAFX_HOME" ]; then
	java -Djava.library.path="." --module-path "$JAVAFX_HOME"/lib $JAVAFX_MODULES -cp "$SALMON_PATH" com.mku.salmon.vault.main.Main
else
	echo "Could not find JavaFx. Edit this file and set JAVAFX_HOME to the directory where you installed JavaFx 17"
	echo "Press any key to exit"
	read -n1
	exit 1
fi
