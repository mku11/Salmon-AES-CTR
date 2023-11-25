package com.mku.salmon.vault.controller;
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

import com.mku.salmon.vault.model.SalmonSettings;
import com.mku.salmon.vault.utils.WindowUtils;
import com.mku.salmon.vault.config.SalmonConfig;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.stream.Stream;

public class SettingsController {

    @FXML
    private ComboBox<String> aesType;
    @FXML
    private ComboBox<String> pbkdfType;

    @FXML
    private ComboBox<String> pbkdfAlgo;

    @FXML
    private ComboBox<String> authType;
    @FXML
    private CheckBox deleteSourceAfterImport;
    private Stage stage;

    @FXML
    private void initialize() {
        String[] aesTypes = Stream.of(SalmonSettings.AESType.values()).map(SalmonSettings.AESType::name).toArray(String[]::new);
        aesType.getItems().setAll(aesTypes);
        aesType.getSelectionModel().select(SalmonSettings.getInstance().getAesType().ordinal());

        String[] pbkdfTypes = Stream.of(SalmonSettings.PbkdfImplType.values()).map(SalmonSettings.PbkdfImplType::name).toArray(String[]::new);
        pbkdfType.getItems().setAll(pbkdfTypes);
        pbkdfType.getSelectionModel().select(SalmonSettings.getInstance().getPbkdfImpl().ordinal());

        String[] pbkdfAlgos = Stream.of(SalmonSettings.PbkdfAlgoType.values()).map(SalmonSettings.PbkdfAlgoType::name).toArray(String[]::new);
        pbkdfAlgo.getItems().setAll(pbkdfAlgos);
        pbkdfAlgo.getSelectionModel().select(SalmonSettings.getInstance().getPbkdfAlgo().ordinal());

        String[] authTypes = Stream.of(SalmonSettings.AuthType.values()).map(SalmonSettings.AuthType::name).toArray(String[]::new);
        authType.getItems().setAll(authTypes);
        authType.getSelectionModel().select(SalmonSettings.getInstance().getSequencerAuthType().ordinal());

        deleteSourceAfterImport.setSelected(SalmonSettings.getInstance().isDeleteAfterImport());

    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    private boolean isDeleteAfterImportSelected() {
        return deleteSourceAfterImport.isSelected();
    }

    private SalmonSettings.PbkdfImplType getpbkdfType() {
        return SalmonSettings.PbkdfImplType.valueOf(pbkdfType.getSelectionModel().getSelectedItem());
    }

    private SalmonSettings.PbkdfAlgoType getpbkdfAlgo() {
        return SalmonSettings.PbkdfAlgoType.valueOf(pbkdfAlgo.getSelectionModel().getSelectedItem());
    }

    private SalmonSettings.AESType getAESType() {
        return SalmonSettings.AESType.valueOf(aesType.getSelectionModel().getSelectedItem());
    }

    private SalmonSettings.AuthType getAuthType() {
        return SalmonSettings.AuthType.valueOf(authType.getSelectionModel().getSelectedItem());
    }

    public static void openSettings(Stage owner) throws IOException {
        FXMLLoader loader = new FXMLLoader(SalmonSettings.getInstance().getClass().getResource("/view/settings.fxml"));
        Parent root = loader.load();
        SettingsController controller = loader.getController();
        Stage stage = new Stage();
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initOwner(owner);
        controller.setStage(stage);
        stage.getIcons().add(WindowUtils.getDefaultIcon());
        stage.setTitle("Settings");
        Scene scene = new Scene(root);
        stage.setScene(scene);
        WindowUtils.setDefaultIconPath(SalmonConfig.icon);
        stage.showAndWait();

        SalmonSettings.getInstance().setAesType(controller.getAESType());
        SalmonSettings.getInstance().setPbkdfImpl(controller.getpbkdfType());
        SalmonSettings.getInstance().setPbkdfAlgo(controller.getpbkdfAlgo());
        SalmonSettings.getInstance().setSequencerAuthType(controller.getAuthType());
        SalmonSettings.getInstance().setDeleteAfterImport(controller.isDeleteAfterImportSelected());
    }
}
