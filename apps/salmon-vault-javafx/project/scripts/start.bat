:: set the javafx library path
set JAVA_FX_PATH="."
set JAVA_FX_MODULES=--add-modules=javafx.controls --add-modules=javafx.swing --add-modules=javafx.fxml --add-modules=javafx.media --add-modules=javafx.graphics

java --module-path %JAVA_FX_PATH% %JAVA_FX_MODULES% -cp "./*" com.mku.salmon.vault.main.Main