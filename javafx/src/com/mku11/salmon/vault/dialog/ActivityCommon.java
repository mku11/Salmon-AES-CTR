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

import com.mku11.salmon.vault.settings.Settings;
import com.mku11.salmon.vault.prefs.Preferences;
import com.mku11.salmonfs.SalmonAuthException;
import com.mku11.salmonfs.SalmonDriveManager;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Function;

public class ActivityCommon {
    static final String TAG = ActivityCommon.class.getName();

    public static void runLater(Runnable runnable, int delay) {
        KeyFrame keyFrame = new KeyFrame(Duration.millis(delay), (e) -> runnable.run());
        Timeline timeline = new Timeline(keyFrame);
        Platform.runLater(timeline::play);
    }

    public interface OnTextSubmittedListener {
        void onTextSubmitted(String text, Boolean option);
    }

    public static boolean setVaultFolder(String dirPath) {
        Settings.getInstance().vaultLocation = dirPath;
        Preferences.savePrefs();
        try {
            SalmonDriveManager.setDriveLocation(dirPath);
            SalmonDriveManager.getDrive().setEnableIntegrityCheck(true);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public static void promptPassword(Function<Void, Void> onAuthenticationSucceded) {
        SalmonAlert alert = new SalmonAlert(Alert.AlertType.NONE, "", ButtonType.OK, ButtonType.CANCEL);
        alert.setTitle("Password");
        PasswordField pass = new PasswordField();
        VBox box = new VBox();
        Label status = new Label();
        box.getChildren().addAll(pass, status);
        alert.getDialogPane().setContent(box);
        alert.show();
        final Button btOk = (Button) alert.getDialogPane().lookupButton(ButtonType.OK);
        btOk.addEventFilter(ActionEvent.ACTION, event -> {
                    String password = pass.getText();
                    try {
                        SalmonDriveManager.getDrive().authenticate(password);
                        if (onAuthenticationSucceded != null)
                            onAuthenticationSucceded.apply(null);
                    } catch (SalmonAuthException ex) {
                        event.consume();
                        status.setText("Incorrect Password");
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
        );
        pass.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER)
                btOk.fire();
        });
        pass.requestFocus();
        pass.positionCaret(0);
    }

    public static void promptSetPassword(Function<String, Void> OnPasswordChanged) {
        SalmonAlert alert = new SalmonAlert(Alert.AlertType.NONE, "", ButtonType.OK, ButtonType.CANCEL);
        alert.setTitle("Set Password");
        PasswordField pass = new PasswordField();
        pass.setPromptText("type password");
        PasswordField passValidate = new PasswordField();
        passValidate.setPromptText("retype password");
        VBox box = new VBox();
        box.getChildren().addAll(pass, passValidate);
        alert.getDialogPane().setContent(box);
        alert.show();
        final Button btOk = (Button) alert.getDialogPane().lookupButton(ButtonType.OK);
        btOk.addEventFilter(ActionEvent.ACTION, event -> {
                    String password = pass.getText();
                    String passwordValidate = passValidate.getText();
                    if (!password.equals(passwordValidate)) {
                        event.consume();
                    } else {
                        try {
                            SalmonDriveManager.getDrive().setPassword(password);
                            if (OnPasswordChanged != null)
                                OnPasswordChanged.apply(password);
                        } catch (SalmonAuthException ex) {
                            ex.printStackTrace();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
        );
    }

    public static void promptEdit(String title, String msg, String value, String option, boolean isFileName,
                                  OnTextSubmittedListener OnEdit) {
        SalmonAlert alert = new SalmonAlert(Alert.AlertType.NONE, "", ButtonType.OK, ButtonType.CANCEL);
        alert.setTitle(title);
        Label msgText = new Label();
        msgText.setText(msg);
        TextField valueText = new TextField();
        valueText.setText(value);
        CheckBox optionCheckBox = new CheckBox();
        VBox box = new VBox();
        box.setSpacing(10);
        box.getChildren().addAll(msgText, valueText);
        if (option != null) {
            optionCheckBox.setText(option);
            box.getChildren().add(optionCheckBox);
        }
        alert.getDialogPane().setContent(box);
        alert.show();
        final Button btOk = (Button) alert.getDialogPane().lookupButton(ButtonType.OK);
        valueText.requestFocus();
        if (isFileName)
        {
            String ext = SalmonDriveManager.getDrive().getExtensionFromFileName(value);
            if(ext != null && ext.length() > 0)
                valueText.selectRange(0, value.length() - ext.length() - 1);
            else
                valueText.selectRange(0, value.length());
        }
        else
        {
            valueText.selectAll();
        }
        valueText.setOnKeyPressed(event -> btOk.fire());
        btOk.addEventFilter(ActionEvent.ACTION, event -> {
                    if (OnEdit != null)
                        OnEdit.onTextSubmitted(valueText.getText(), optionCheckBox.isSelected());
                }
        );
    }

    public static void promptDialog(String title, String body,
                                    String buttonLabel1, Function<ButtonType, Void> buttonListener1,
                                    String buttonLabel2, Function<ButtonType, Void> buttonListener2) {
        ButtonType ok = new ButtonType(buttonLabel1, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(buttonLabel2, ButtonBar.ButtonData.CANCEL_CLOSE);
        SalmonAlert alert;
        if (buttonLabel2 != null)
            alert = new SalmonAlert(javafx.scene.control.Alert.AlertType.NONE, body, ok, cancel);
        else
            alert = new SalmonAlert(javafx.scene.control.Alert.AlertType.NONE, body, ok);

        if (title != null)
            alert.setTitle(title);
        alert.setContentText(body);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.orElse(cancel) == ok && buttonListener1 != null) {
            buttonListener1.apply(ok);
        } else if (buttonListener2 != null) {
            buttonListener2.apply(cancel);
        }
    }

}
