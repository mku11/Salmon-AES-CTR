package com.mku11.salmon.vault.main;
/*
MIT License

Copyright (c) 2021 Max Kas

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

import com.mku11.salmon.vault.config.Config;
import com.mku11.salmon.vault.controller.MainController;
import com.mku11.salmon.vault.dialog.SalmonAlert;
import com.mku11.salmon.vault.prefs.Preferences;
import com.mku11.salmon.vault.settings.Settings;
import com.mku11.salmon.vault.window.Window;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        Window.setDefaultIconPath(Config.icon);
        SalmonAlert.setDefaultStyleSheet(Config.css);
        if(Config.enableNativeLib) {
            System.loadLibrary(Config.LIB_NAME);
        }
        Preferences.loadPrefs(Settings.getInstance());
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/main.fxml"));
        Parent root = loader.load();
        MainController controller = loader.getController();
        Scene scene = new Scene(root);
        stage.setMinWidth(400);
        stage.setMinHeight(300);
        stage.setScene(scene);
        stage.setTitle(Config.APP_NAME);
        stage.getIcons().add(Window.getDefaultIcon());
        controller.setStage(stage);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
