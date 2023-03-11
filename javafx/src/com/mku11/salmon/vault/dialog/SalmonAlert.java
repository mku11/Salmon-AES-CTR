package com.mku11.salmon.vault.dialog;
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

import com.mku11.salmon.vault.window.Window;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

public class SalmonAlert extends javafx.scene.control.Alert {
    private static String defaultStyleSheet;

    public SalmonAlert(AlertType alertType, String content) {
        super(alertType, content);
        setupIcon();
        setupStyle();
    }

    public static void setDefaultStyleSheet(String defaultStyleSheet) {
        SalmonAlert.defaultStyleSheet = defaultStyleSheet;
    }

    private void setupIcon() {
        Image icon = Window.getDefaultIcon();
        Stage dialogStage = (Stage) getDialogPane().getScene().getWindow();
        dialogStage.getIcons().add(icon);
    }

    public SalmonAlert(AlertType confirmation, String content, ButtonType buttonTypeOk, ButtonType buttonTypeCancel) {
        super(confirmation, content, buttonTypeOk, buttonTypeCancel);
        setupIcon();
        setupStyle();
    }

    public SalmonAlert(AlertType confirmation, String content, ButtonType buttonTypeOk) {
        super(confirmation, content, buttonTypeOk);
        setupIcon();
        setupStyle();
    }


    public SalmonAlert(AlertType confirmation, String content, ButtonType buttonType1, ButtonType buttonType2, ButtonType buttonType3) {
        super(confirmation, content, buttonType1, buttonType2, buttonType3);
        setupIcon();
        setupStyle();
    }


    private void setupStyle() {
        Stage dialogStage = (Stage) getDialogPane().getScene().getWindow();
        Scene scene = dialogStage.getScene();
        scene.getStylesheets().add(defaultStyleSheet);
        getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
    }

}
