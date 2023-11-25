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

import com.mku.io.MemoryStream;
import com.mku.salmon.io.SalmonStream;
import com.mku.salmon.vault.config.SalmonConfig;
import com.mku.salmon.vault.dialog.SalmonDialog;
import com.mku.salmon.vault.model.SalmonSettings;
import com.mku.salmon.vault.model.SalmonTextEditor;
import com.mku.salmon.vault.model.SalmonVaultManager;
import com.mku.salmon.vault.utils.WindowUtils;
import com.mku.salmon.vault.viewmodel.SalmonFileViewModel;
import com.mku.salmonfs.SalmonFile;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class TextEditorController {

    private Stage stage;
    private SalmonFileViewModel item;

    @FXML
    private TextArea contentArea;

    @FXML
    public TextField searchText;

    @FXML
    public Button searchButton;

    @FXML
    public Label status;

    private int currentCaretPosition = 0;
    private SalmonTextEditor editor;

    @FXML
    private void initialize() {
        editor = new SalmonTextEditor();
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public static void openTextEditor(SalmonFileViewModel file, Stage owner) throws IOException {
        FXMLLoader loader = new FXMLLoader(SalmonSettings.getInstance().getClass().getResource("/view/text-editor.fxml"));
        Parent root = loader.load();
        TextEditorController controller = loader.getController();
        Stage stage = new Stage();
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initOwner(owner);
        controller.setStage(stage);
        controller.load(file);
        stage.getIcons().add(WindowUtils.getDefaultIcon());
        stage.setTitle("TextEditor");
        Scene scene = new Scene(root);
        stage.setScene(scene);
        WindowUtils.setDefaultIconPath(SalmonConfig.icon);
        stage.showAndWait();
    }

    private void load(SalmonFileViewModel item) {
        this.item = item;
        String content;
        try {
            content = getTextContent(item.getSalmonFile());
            contentArea.setText(content);
            ShowTaskMessage("File loaded");
            WindowUtils.runOnMainThread(() ->
            {
                ShowTaskMessage("");
            }, 2000);
            contentArea.requestFocus();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getTextContent(SalmonFile file) throws Exception {
        SalmonStream stream = file.getInputStream();
        MemoryStream ms = new MemoryStream();
        stream.copyTo(ms);
        stream.close();
        byte[] bytes = ms.toArray();
        String content = new String(bytes, StandardCharsets.UTF_8);
        return content;
    }

    public void onClose() {
        stage.close();
    }

    public synchronized void onSave() {
        SalmonFile oldFile = item.getSalmonFile();
        SalmonFile targetFile = editor.OnSave(item.getSalmonFile(), contentArea.getText());
        int index = SalmonVaultManager.getInstance().getFileItemList().indexOf(oldFile);
        if (index >= 0) {
            SalmonVaultManager.getInstance().getFileItemList().remove(oldFile);
            SalmonVaultManager.getInstance().getFileItemList().add(index, targetFile);
        }
        try {
            item.setSalmonFile(targetFile);
            ShowTaskMessage("File saved");
            WindowUtils.runOnMainThread(() ->
            {
                ShowTaskMessage("");
            }, 2000);
        } catch (Exception e) {
            SalmonDialog.promptDialog("Could not save file: " + e.getMessage());
        }
    }

    public void ShowTaskMessage(String msg) {
        WindowUtils.runOnMainThread(() ->
        {
            status.setText(msg != null ? msg : "");
        });
    }

    public void onTextKeyReleased(KeyEvent keyEvent) {
        switch (keyEvent.getCode()) {
            case S:
                if (keyEvent.isControlDown())
                    onSave();
                break;
        }
    }

    public void onFind() {
        searchText.requestFocus();
        searchText.selectAll();
    }

    public void onSearch() {
        search(searchText.getText(), contentArea.getCaretPosition() - contentArea.getAnchor() > 0 ? contentArea.getAnchor() + 1 : contentArea.getAnchor());
    }

    public void search(String text, int caretPosition) {
        int searchStart;
        if (currentCaretPosition == -1) {
            searchStart = 0;
        } else if (currentCaretPosition != caretPosition) {
            searchStart = caretPosition;
        } else {
            searchStart = currentCaretPosition;
        }
        int start = contentArea.getText().indexOf(text, searchStart);
        if (start >= 0) {
            selectAndScrollTo(start, text.length());
            currentCaretPosition = start + 1;
        } else {
            currentCaretPosition = -1;
        }
    }

    public void selectAndScrollTo(int start, int length) {
        WindowUtils.runOnMainThread(() ->
        {
            contentArea.requestFocus();
            contentArea.positionCaret(start);
            contentArea.selectPositionCaret(start + length);
            searchText.requestFocus();
        });
    }
}
