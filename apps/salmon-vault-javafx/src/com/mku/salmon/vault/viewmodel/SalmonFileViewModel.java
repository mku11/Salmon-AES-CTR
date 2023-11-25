package com.mku.salmon.vault.viewmodel;
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

import com.mku.salmon.SalmonSecurityException;
import com.mku.salmon.integrity.SalmonIntegrityException;
import com.mku.salmon.vault.image.Thumbnails;
import com.mku.salmon.vault.utils.ByteUtils;
import com.mku.salmonfs.SalmonAuthException;
import com.mku.salmonfs.SalmonFile;
import com.mku.utils.SalmonFileUtils;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.WritableValue;
import javafx.fxml.FXML;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SalmonFileViewModel {
    private static final int BACKGROUND_THREADS = 4;
    private static final SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy hh:mm:ss a");

    private SimpleObjectProperty<ImageView> image = new SimpleObjectProperty<>();
    private final SimpleStringProperty name = new SimpleStringProperty();
    private final SimpleStringProperty date = new SimpleStringProperty();
    private final SimpleStringProperty type = new SimpleStringProperty();
    private final SimpleStringProperty size = new SimpleStringProperty();
    private final SimpleStringProperty path = new SimpleStringProperty();

    private SalmonFile salmonFile;

    public SalmonFileViewModel(SalmonFile salmonFile) {
        this.salmonFile = salmonFile;
    }

    @FXML
    public SimpleObjectProperty<ImageView> imageProperty(){
        if(image.get() != null)
            return image;
        final ImageView imageView = new ImageView();
        imageView.setFitHeight(48);
        imageView.setPreserveRatio(true);
        Image thumbnail = Thumbnails.generateThumbnail(salmonFile, imageView);
        if (image != null)
            imageView.setImage(thumbnail);
        image.setValue(imageView);
        return image;
    }

    @FXML
    public SimpleStringProperty nameProperty() {
        if(name.get() == null)
            updateProperty(() -> salmonFile.getBaseName(), this.name);
        return name;
    }

    @FXML
    public SimpleStringProperty dateProperty() {
        if(date.get() == null)
            updateProperty(this::getDateText, this.date);
        return date;
    }

    @FXML
    public SimpleStringProperty typeProperty() {
        if(type.get() == null)
            updateProperty(this::getExtText, this.type);
        return type;
    }

    @FXML
    public SimpleStringProperty sizeProperty() {
        if(size.get() == null)
            updateProperty(this::getSizeText, this.size);
        return size;
    }
    @FXML
    public SimpleStringProperty pathProperty() {
        if(path.get() == null)
            updateProperty(() -> salmonFile.getPath(), this.path);
        return path;
    }

    private <T> void updateProperty(Callable<T> callable, WritableValue<T> property) {
        CompletableFuture.supplyAsync(() -> {
            try {
                return callable.call();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }).thenAccept((value) -> {
            Platform.runLater(() -> property.setValue(value));
        });
    }

    public void setSalmonFile(SalmonFile file) throws Exception {
        salmonFile = file;
        update();
    }

    public void update() {
        try {
            name.setValue(salmonFile.getBaseName());
            date.setValue(getDateText());
            size.setValue(getSizeText());
            type.setValue(getExtText());
            path.setValue(salmonFile.getPath());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private String getExtText() throws SalmonSecurityException, SalmonIntegrityException, IOException, SalmonAuthException {
        return SalmonFileUtils.getExtensionFromFileName(salmonFile.getBaseName()).toLowerCase();
    }

    private String getDateText() {
        return formatter.format(new Date(salmonFile.getLastDateTimeModified()));
    }

    private String getSizeText() {
        if (!salmonFile.isDirectory())
            return ByteUtils.getBytes(salmonFile.getRealFile().length(), 2);
        else {
            int items = salmonFile.getChildrenCount();
            return items + " item" + (items == 1 ? "" : "s");
        }
    }

    public SalmonFile getSalmonFile() {
        return salmonFile;
    }

    public void rename(String newValue) throws Exception {
        salmonFile.rename(newValue);
        name.setValue(salmonFile.getBaseName());
    }
}
