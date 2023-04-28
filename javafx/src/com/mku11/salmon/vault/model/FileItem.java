package com.mku11.salmon.vault.model;
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
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.image.ImageView;

public abstract class FileItem {
    @FXML
    protected final SimpleStringProperty name = new SimpleStringProperty("");
    @FXML
    protected final SimpleStringProperty date = new SimpleStringProperty("");
    @FXML
    protected final SimpleStringProperty type = new SimpleStringProperty("");
    @FXML
    protected final SimpleStringProperty size = new SimpleStringProperty("");
    @FXML
    protected final SimpleStringProperty path = new SimpleStringProperty("");

    @FXML
    public SimpleStringProperty nameProperty() {
        return name;
    }
    @FXML
    public String getName() {
        return name.getValue();
    }
    @FXML
    public String getDate() {
        return date.get();
    }
    @FXML
    public SimpleStringProperty dateProperty() {
        return date;
    }
    @FXML
    public String getType() {
        return type.get();
    }
    @FXML
    public SimpleStringProperty typeProperty() {
        return type;
    }
    @FXML
    public String getSize() {
        return size.get();
    }
    @FXML
    public SimpleStringProperty sizeProperty() {
        return size;
    }
    @FXML
    public String getPath() {
        return path.get();
    }
    @FXML
    public SimpleStringProperty pathProperty() {
        return path;
    }

    public abstract ImageView getImage() throws Exception;

    public abstract boolean isDirectory();

    public abstract String getBaseName() throws Exception;

    public abstract FileItem[] listFiles() throws Exception;

    public abstract long getFileSize() throws Exception;

    public abstract Object getTag();

    public abstract void createDirectory(String folderName, byte[] key, byte[] dirNameNonce) throws Exception;

    public abstract long getLastDateTimeModified();

    public abstract void delete();

    public abstract void rename(String newValue) throws Exception;

    public abstract void update() throws Exception;
}
