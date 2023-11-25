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

import com.mku.handler.SalmonStreamHandlerFactory;
import com.mku.salmon.vault.config.SalmonConfig;
import com.mku.salmon.vault.model.SalmonSettings;
import com.mku.salmon.vault.utils.WindowUtils;
import com.mku.salmon.vault.viewmodel.SalmonFileViewModel;
import com.mku.salmonfs.SalmonFile;
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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MediaPlayerController {
    private static int MEDIA_BUFFERS = 4;
    private static int MEDIA_BUFFER_SIZE = 4 * 1024 * 1024;
    private static int MEDIA_THREADS = 1;
    private static int MEDIA_BACKOFFSET = 256 * 1024;

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

    public final void setImage(Image image) {
        this.image.set(image);
    }

    public final Image getImage() {
        return image.get();
    }

    public final ObjectProperty<Image> imageProperty() {
        return image;
    }

    private final SimpleStringProperty currtime = new SimpleStringProperty(this, "00:00:00");

    public final void setCurrtime(String value) {
        this.currtime.set(value);
    }

    public final String getCurrtime() {
        return currtime.get();
    }

    public final SimpleStringProperty currtimeProperty() {
        return currtime;
    }

    private final SimpleStringProperty totaltime = new SimpleStringProperty(this, "00:00:00");

    public final void setTotaltime(String value) {
        this.totaltime.set(value);
    }

    public final String getTotaltime() {
        return totaltime.get();
    }

    public final SimpleStringProperty totaltimeProperty() {
        return totaltime;
    }

    private Image playImage = new Image(this.getClass().getResourceAsStream("/icons/play.png"));
    private Image pauseImage = new Image(this.getClass().getResourceAsStream("/icons/pause.png"));

    private SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss");

    @FXML
    private void initialize() {
        setImage(playImage);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    private Executor executor = Executors.newSingleThreadExecutor();

    public void setStage(Stage stage) {
        this.stage = stage;
        stage.setOnCloseRequest(event -> {
            stopTimer();
            mp.stop();
        });
    }

    public static void openMediaPlayer(SalmonFileViewModel file, Stage owner) throws IOException {
        FXMLLoader loader = new FXMLLoader(SalmonSettings.getInstance().getClass().getResource("/view/media-player.fxml"));
        Parent root = loader.load();
        MediaPlayerController controller = loader.getController();
        Stage stage = new Stage();
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initOwner(owner);
        controller.setStage(stage);
        controller.load(file);
        stage.getIcons().add(WindowUtils.getDefaultIcon());
        stage.setTitle("Media Player");
        Scene scene = new Scene(root);
        stage.setScene(scene);
        WindowUtils.setDefaultIconPath(SalmonConfig.icon);
        stage.setMinHeight(600);
        stage.setMinWidth(800);
        stage.show();
        controller.play();
    }

    private void play() {
        mp.play();
    }

    static {
        URL.setURLStreamHandlerFactory(new SalmonStreamHandlerFactory(
                MEDIA_BUFFERS, MEDIA_BUFFER_SIZE,
                MEDIA_THREADS, MEDIA_BACKOFFSET));
    }

    private void load(SalmonFileViewModel fileItem) throws UnsupportedEncodingException {
        SalmonFile file = fileItem.getSalmonFile();
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
        mp.setOnPaused(() -> setImage(playImage));
        mp.setOnPlaying(() -> setImage(pauseImage));
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

    public void onClose() {
        stopTimer();
        mp.stop();
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
