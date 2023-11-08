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

import com.mku.io.InputStreamWrapper;
import com.mku.salmon.vault.config.SalmonConfig;
import com.mku.salmon.vault.model.SalmonImageViewer;
import com.mku.salmon.vault.model.SalmonSettings;
import com.mku.salmon.vault.utils.WindowUtils;
import com.mku.salmon.vault.viewmodel.SalmonFileViewModel;
import com.mku.salmonfs.SalmonFile;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.BufferedInputStream;
import java.io.IOException;

public class ImageViewerController {
    private static final int ENC_BUFFER_SIZE = 128 * 1024;

    public ImageView imageView;
    private Stage stage;

    private final ObjectProperty<Image> image = new SimpleObjectProperty<>(this, "image");
    private SalmonImageViewer viewer;

    public final void setImage(Image image) {
        this.image.set(image);
    }

    public final Image getImage() {
        return image.get();
    }

    public final ObjectProperty<Image> imageProperty() {
        return image;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public static void openImageViewer(SalmonFileViewModel file, Stage owner) throws IOException {
        FXMLLoader loader = new FXMLLoader(SalmonSettings.getInstance().getClass().getResource("/view/image-viewer.fxml"));
        Parent root = loader.load();
        ImageViewerController controller = loader.getController();
        Stage stage = new Stage();
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initOwner(owner);
        controller.setStage(stage);
        controller.load(file);
        stage.getIcons().add(WindowUtils.getDefaultIcon());
        stage.setTitle("Image Viewer");
        Scene scene = new Scene(root);
        stage.setScene(scene);
        WindowUtils.setDefaultIconPath(SalmonConfig.icon);
        stage.setMinHeight(600);
        stage.setMinWidth(800);
        stage.show();
    }

    private void load(SalmonFileViewModel file) {
        if(viewer == null)
            viewer = new SalmonImageViewer();
        try {
            viewer.load(file.getSalmonFile());
            BufferedInputStream stream = new BufferedInputStream(viewer.getImageStream(), ENC_BUFFER_SIZE);
            Image image = new Image(stream);
            imageView.setPreserveRatio(true);
            imageView.setImage(image);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void onClose() {
        stage.close();
    }

}
