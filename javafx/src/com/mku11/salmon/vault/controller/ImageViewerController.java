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
import java.io.IOException;
import java.io.InputStream;

public class ImageViewerController {

    public ImageView imageView;
    private Stage stage;

    private final ObjectProperty<Image> image = new SimpleObjectProperty<>(this, "image");
    public final void setImage(Image image) { this.image.set(image); }
    public final Image getImage() { return image.get(); }
    public final ObjectProperty<Image> imageProperty() { return image; }

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
            ImageStream mediaStream = new ImageStream(salmonFile);
            BufferedImage bimage = ImageIO.read(mediaStream);
            image = SwingFXUtils.toFXImage(bimage, null);
            imageView.setImage(image);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void onClose() {
        stage.close();
    }

    // might be slower
    static class ImageStream extends InputStream {
        private SalmonStream salmonStream = null;

        ImageStream(SalmonFile salmonFile) {
            try {
                salmonStream = salmonFile.getInputStream();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public long skip(long pos) {
            long currPos = 0;
            long newPos = 0;
            try {
                currPos =  salmonStream.position();
                salmonStream.seek(pos, AbsStream.SeekOrigin.Current);
                newPos = salmonStream.position();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return newPos - currPos;
        }

        @Override
        public int read() {
            byte[] buffer = new byte[1];
            int bytesRead = 0;
            try {
                bytesRead = salmonStream.read(buffer, 0, 1);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return bytesRead;
        }

        public int read(byte[] buffer, int start, int length) {
            int bytesRead = 0;
            try {
                bytesRead = salmonStream.read(buffer, start, length);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return bytesRead;
        }
    }

    public void shortcutPressed(KeyEvent keyEvent) {
    }
}
