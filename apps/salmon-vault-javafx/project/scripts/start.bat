@echo off

:: uncomment and enter the javafx library path
:: set JAVAFX_HOME="D:\tools\javafx-sdk-17.0.10"

set SALMON_PATH="./*"
set JAVAFX_MODULES=--add-modules=javafx.controls --add-modules=javafx.swing --add-modules=javafx.fxml --add-modules=javafx.media --add-modules=javafx.graphics

if exist "%JAVAFX_HOME%" (
  java -Djava.library.path="." --module-path %JAVAFX_HOME%\lib %JAVAFX_MODULES% -cp %SALMON_PATH% com.mku.salmon.vault.main.Main
) else (
  echo Could not find JavaFx. Edit this file and set JAVAFX_HOME to the directory where you installed JavaFx 17
  set /p key="Press any key to exit"
)