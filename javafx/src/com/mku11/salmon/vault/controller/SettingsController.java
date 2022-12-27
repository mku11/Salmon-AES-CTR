package com.mku11.salmon.vault.controller;
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
import com.mku11.salmon.vault.dialog.ActivityCommon;
import com.mku11.salmon.vault.prefs.Preferences;
import com.mku11.salmon.vault.settings.Settings;
import com.mku11.salmon.vault.window.Window;
import com.mku11.salmonfs.SalmonAuthException;
import com.mku11.salmonfs.SalmonDriveManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.stream.Stream;

public class SettingsController {

    @FXML
    private ComboBox<String> aesType;
    @FXML
    private ComboBox<String> pbkdfType;
    @FXML
    private CheckBox deleteSourceAfterImport;
    @FXML
    private CheckBox enableLogs;
    @FXML
    private CheckBox enableDetailedLogs;
    private Stage stage;

    @FXML
    private void initialize() {
        String[] aesTypes = Stream.of(Settings.AESType.values()).map(Settings.AESType::name).toArray(String[]::new);
        aesType.getItems().setAll(aesTypes);
        aesType.getSelectionModel().select(Settings.getInstance().aesType.ordinal());

        String[] pbkdfTypes = Stream.of(Settings.PbkdfType.values()).map(Settings.PbkdfType::name).toArray(String[]::new);
        pbkdfType.getItems().setAll(pbkdfTypes);
        pbkdfType.getSelectionModel().select(Settings.getInstance().pbkdfType.ordinal());

        deleteSourceAfterImport.setSelected(Settings.getInstance().deleteAfterImport);
        enableLogs.setSelected(Settings.getInstance().enableLog);
        enableDetailedLogs.setSelected(Settings.getInstance().enableLogDetails);
        if(Settings.getInstance().vaultLocation != null)
            vaultlocation.setValue("Location: " + Settings.getInstance().vaultLocation);

    }
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    private void changeVaultLocation() {
        File selectedDirectory = MainController.selectVault(stage);
        if(selectedDirectory == null)
            return;
        String filePath = selectedDirectory.getAbsolutePath();
        try {
            ActivityCommon.setVaultFolder(filePath);
        } catch (Exception e) {
            new Alert(Alert.AlertType.WARNING, "Could not change vault location").show();
        }
        vaultlocation.setValue("Location: " + filePath);
        //TODO: notify the main screen to refresh
    }

    @FXML
    private void changePassword() {
        ActivityCommon.promptSetPassword( (String pass) ->
        {
            try {
                SalmonDriveManager.getDrive().getVirtualRoot();
                //TODO: notify main screen to refresh
            } catch (SalmonAuthException e) {
                e.printStackTrace();
            }
            return null;
        });
    }

    private boolean isEnabledDetailedLog() {
        return enableDetailedLogs.isSelected();
    }

    private boolean isEnabledLog() {
        return enableLogs.isSelected();
    }

    private boolean isDeleteAfterImportSelected() {
        return deleteSourceAfterImport.isSelected();
    }

    private Settings.PbkdfType getpbkdfType() {
        return Settings.PbkdfType.valueOf(pbkdfType.getSelectionModel().getSelectedItem());
    }

    private Settings.AESType getAESType() {
        return Settings.AESType.valueOf(aesType.getSelectionModel().getSelectedItem());
    }

    public static void openSettings(Stage owner) throws IOException {
        FXMLLoader loader = new FXMLLoader(Settings.getInstance().getClass().getResource("/view/settings.fxml"));
        Parent root = loader.load();
        SettingsController controller = loader.getController();
        Stage stage = new Stage();
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initOwner(owner);
        controller.setStage(stage);
        stage.getIcons().add(Window.getDefaultIcon());
        stage.setTitle("Settings");
        Scene scene = new Scene(root);
        stage.setScene(scene);
        Window.setDefaultIconPath(Config.icon);
        stage.showAndWait();

        Settings.getInstance().aesType = controller.getAESType();
        Settings.getInstance().pbkdfType = controller.getpbkdfType();
        Settings.getInstance().deleteAfterImport = controller.isDeleteAfterImportSelected();
        Settings.getInstance().enableLog = controller.isEnabledLog();
        Settings.getInstance().enableLogDetails = controller.isEnabledDetailedLog();
        Preferences.savePrefs();
    }


    private final SimpleStringProperty vaultlocation = new SimpleStringProperty("Location: None");

    public String getVaultlocation()
    {
        return vaultlocation.get();
    }

    public void setVaultlocation(String value)
    {
        vaultlocation.set(value);
    }

    public SimpleStringProperty vaultlocationProperty() {
        return vaultlocation;
    }

}
