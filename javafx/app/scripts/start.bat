REM Change the module path to point to the javafx sdk directory
REM If you use AES intrinsics mek sure this file and salmon.dll are in the same directory

:: set the javafx library path
set JAVA_FX_PATH="D:\tools\javafx-sdk-19\lib"

java --module-path %JAVA_FX_PATH% --add-modules=javafx.controls --add-modules=javafx.swing --add-modules=javafx.fxml --add-modules=javafx.media -jar salmon-java-app.jar