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
import com.mku11.salmon.vault.settings.Settings;
import com.mku11.salmon.vault.window.Window;
import com.mku11.salmonfs.SalmonFile;
import com.mku11.stream.SalmonStreamHandlerFactory;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.URLStreamHandler;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;


public class MediaPlayerController {
    @FXML
    public Button playButton;
    @FXML
    public Slider slider;

    private Stage stage;

    @FXML
    private MediaView mediaView;
    private MediaPlayer mp;

    private boolean quit = false;

    private final ObjectProperty<Image> image = new SimpleObjectProperty<>(this, "image");
    public final void setImage(Image image) { this.image.set(image); }
    public final Image getImage() { return image.get(); }
    public final ObjectProperty<Image> imageProperty() { return image; }

    private final SimpleStringProperty currtime = new SimpleStringProperty(this, "00:00:00");
    public final void setCurrtime(String value) { this.currtime.set(value); }
    public final String getCurrtime() { return currtime.get(); }
    public final SimpleStringProperty currtimeProperty() { return currtime; }

    private final SimpleStringProperty totaltime = new SimpleStringProperty(this, "00:00:00");
    public final void setTotaltime(String value) { this.totaltime.set(value); }
    public final String getTotaltime() { return totaltime.get(); }
    public final SimpleStringProperty totaltimeProperty() { return totaltime; }

    Image playImage = new Image(this.getClass().getResourceAsStream("/icons/play.png"));
    Image pauseImage = new Image(this.getClass().getResourceAsStream("/icons/pause.png"));

    SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss");

    @FXML
    private void initialize() {
        setImage(playImage);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    Executor executor = Executors.newSingleThreadExecutor();

    public void setStage(Stage stage) {
        this.stage = stage;
        stage.setOnCloseRequest(event -> {
            stopTimer();
            mp.stop();
        });
    }

    public static void openMediaPlayer(FileItem file, Stage owner, MainController.MediaType type) throws IOException {
        FXMLLoader loader = new FXMLLoader(Settings.getInstance().getClass().getResource("/view/media-player.fxml"));
        Parent root = loader.load();
        MediaPlayerController controller = loader.getController();
        Stage stage = new Stage();
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initOwner(owner);
        controller.setStage(stage);
        controller.load(file, type);
        stage.getIcons().add(Window.getDefaultIcon());
        stage.setTitle("Media Player");
        Scene scene = new Scene(root);
        stage.setScene(scene);
        Window.setDefaultIconPath(Config.icon);
        stage.setMinHeight(600);
        stage.setMinWidth(800);
        stage.show();
        controller.play();
    }

    private void play() {
        mp.play();
    }

    static {
        URL.setURLStreamHandlerFactory(new SalmonStreamHandlerFactory());
    }

    private void load(FileItem fileItem, MainController.MediaType type) throws UnsupportedEncodingException {
        SalmonFile file = ((SalmonFileItem) fileItem).getSalmonFile();
        String filePath;
        try {
            filePath = file.getRealPath();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        filePath = URLEncoder.encode(filePath, "UTF-8");
        String uri = "http://localhost/" + filePath;

        Media m = new Media(uri);
        mp = new MediaPlayer(m);
        mp.setOnStopped(new Runnable() {
            @Override
            public void run() {
                mp.dispose();
            }
        });
        mp.setOnPaused(new Runnable() {
            @Override
            public void run() {
                setImage(playImage);
            }
        });
        mp.setOnPlaying(new Runnable() {
            @Override
            public void run() {
                setImage(pauseImage);
            }
        });
        mediaView.setMediaPlayer(mp);
        startTimer();
    }

    private void stopTimer() {
        quit = true;
    }

    @SuppressWarnings("BusyWait")
    private void startTimer() {
        Thread timer = new Thread(() -> {
            while (!quit) {
                int progressInt = (int) (mp.getCurrentTime().toMillis() / mp.getTotalDuration().toMillis() * 1000);
                slider.setValue(progressInt);
                Date curr = new Date((long) mp.getCurrentTime().toMillis());
                Date total = new Date((long) mp.getTotalDuration().toMillis());
                Platform.runLater(() -> {
                    currtime.setValue(format.format(curr));
                    totaltime.setValue(format.format(total));
                });
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        timer.start();
    }

    public void shortcutPressed(KeyEvent keyEvent) {
    }

    public void onClose() {
        stage.close();
    }

    public void togglePlay() {
        if (mp.getStatus() == MediaPlayer.Status.PLAYING) {
            mp.pause();
        } else
            mp.play();
    }

    public void onSliderChanged(MouseEvent mouseEvent) {
        int posMillis = (int) (mp.getTotalDuration().toMillis() * slider.getValue() / 1000);
        javafx.util.Duration duration = javafx.util.Duration.millis(posMillis);
        mp.seek(duration);
    }
}
