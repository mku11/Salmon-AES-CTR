REM change the module path to point to the javafx sdk directory
REM salmon.dll needs at that same directory
REM for more information see README.md at the root directory

java --module-path "D:\tools\javafx-sdk-19\lib" --add-modules=javafx.controls --add-modules=javafx.swing --add-modules=javafx.fxml --add-modules=javafx.media -jar salmon-java-app.jar