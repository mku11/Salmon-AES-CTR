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
import com.mku11.salmon.streams.MemoryStream;
import com.mku11.salmon.streams.SalmonStream;
import com.mku11.salmon.vault.config.Config;
import com.mku11.salmon.vault.model.FileItem;
import com.mku11.salmon.vault.model.SalmonFileItem;
import com.mku11.salmon.vault.settings.Settings;
import com.mku11.salmon.vault.window.Window;
import com.mku11.salmonfs.SalmonFile;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyEvent;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class TextEditorController {

    private Stage stage;
    private FileItem item;

    @FXML
    private TextArea contentArea;

    @FXML
    private void initialize() {

    }
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public static void openTextEditor(FileItem file, Stage owner) throws IOException {
        FXMLLoader loader = new FXMLLoader(Settings.getInstance().getClass().getResource("/view/text-editor.fxml"));
        Parent root = loader.load();
        TextEditorController controller = loader.getController();
        Stage stage = new Stage();
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initOwner(owner);
        controller.setStage(stage);
        controller.load(file);
        stage.getIcons().add(Window.getDefaultIcon());
        stage.setTitle("TextEditor");
        Scene scene = new Scene(root);
        stage.setScene(scene);
        Window.setDefaultIconPath(Config.icon);
        stage.showAndWait();
    }

    private void load(FileItem item) {
        this.item = item;
        String content;
        try {
            content = getTextContent(((SalmonFileItem) item).getSalmonFile());
            contentArea.setText(content);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private String getTextContent(SalmonFile file) throws Exception {
        SalmonStream stream = file.getInputStream();
        MemoryStream ms = new MemoryStream();
        stream.copyTo(ms);
        stream.close();
        byte [] bytes = ms.toArray();
        String content = new String(bytes);
        return content;
    }

    public void shortcutPressed() {
    }

    public void onClose() {
        stage.close();
    }

    public void onSave() {
        try {
            saveContent();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private synchronized void saveContent() throws Exception {
        byte[] contents = contentArea.getText().getBytes(StandardCharsets.UTF_8);
        MemoryStream ins = new MemoryStream(contents);
        SalmonFile file = ((SalmonFileItem) item).getSalmonFile();
        SalmonFile dir = file.getParent();
        file.delete();
        file = dir.createFile(file.getBaseName());
        SalmonStream stream = file.getOutputStream();
        ins.copyTo(stream);
        stream.flush();
        stream.close();
        ins.close();
        ((SalmonFileItem) item).setSalmonFile(file);
    }

    public void onTextKeyReleased(KeyEvent keyEvent) {
        switch (keyEvent.getCode()) {
            case S:
                if (keyEvent.isControlDown())
                    onSave();
                break;
        }
    }
}
