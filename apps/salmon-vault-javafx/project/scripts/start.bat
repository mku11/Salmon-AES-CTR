:: uncomment and enter the javafx library path
:: set JAVAFX_HOME="D:\tools\javafx-sdk-17.0.10\lib"

set SALMON_PATH="./*"
set JAVAFX_MODULES=--add-modules=javafx.controls --add-modules=javafx.swing --add-modules=javafx.fxml --add-modules=javafx.media --add-modules=javafx.graphics

java --module-path %JAVAFX_HOME% %JAVAFX_MODULES% -cp %SALMON_PATH% com.mku.salmon.vault.main.Main