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

import com.mku11.salmon.streams.AbsStream;
import com.mku11.salmon.streams.InputStreamWrapper;
import com.mku11.salmon.streams.SalmonStream;
import com.mku11.salmon.vault.config.Config;
import com.mku11.salmon.vault.settings.Settings;
import com.mku11.salmon.vault.window.Window;
import com.mku11.salmonfs.SalmonFile;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.stage.Modality;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ImageViewerController {
    private static final int ENC_BUFFER_SIZE = 128 * 1024;

    public ImageView imageView;
    private Stage stage;

    private final ObjectProperty<Image> image = new SimpleObjectProperty<>(this, "image");

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

    public static void openImageViewer(FileItem file, Stage owner) throws IOException {
        FXMLLoader loader = new FXMLLoader(Settings.getInstance().getClass().getResource("/view/image-viewer.fxml"));
        Parent root = loader.load();
        ImageViewerController controller = loader.getController();
        Stage stage = new Stage();
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initOwner(owner);
        controller.setStage(stage);
        controller.load(file);
        stage.getIcons().add(Window.getDefaultIcon());
        stage.setTitle("Image Viewer");
        Scene scene = new Scene(root);
        stage.setScene(scene);
        Window.setDefaultIconPath(Config.icon);
        stage.setMinHeight(600);
        stage.setMinWidth(800);
        stage.show();
    }

    private void load(FileItem file) {
        SalmonFile salmonFile = ((SalmonFileItem) file).getSalmonFile();
        Image image;
        try {
            BufferedInputStream stream = new BufferedInputStream(new InputStreamWrapper(salmonFile.getInputStream()), ENC_BUFFER_SIZE);
            image = new Image(stream);
            imageView.setPreserveRatio(true);
            imageView.setImage(image);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void onClose() {
        stage.close();
    }

    public void shortcutPressed(KeyEvent keyEvent) {
    }
}
