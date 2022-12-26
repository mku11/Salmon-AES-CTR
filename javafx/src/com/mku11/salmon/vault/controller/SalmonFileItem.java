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
import com.mku11.salmon.vault.utils.Utils;
import com.mku11.salmonfs.SalmonDriveManager;
import com.mku11.salmonfs.SalmonFile;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.text.SimpleDateFormat;
import java.util.Date;

public class SalmonFileItem extends FileItem {
    private static final SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy hh:mm:ss a");
    private SalmonFile salmonFile;

    public SalmonFileItem(SalmonFile salmonFile) throws Exception {
        this.salmonFile = salmonFile;
        update();
    }

    public void setSalmonFile(SalmonFile file) throws Exception {
        salmonFile = file;
        update();
    }

    public void update() throws Exception {
        name.setValue(salmonFile.getBaseName());
        Date dt = new Date(salmonFile.getLastDateTimeModified());
        date.setValue(formatter.format(dt));
        if(!salmonFile.isDirectory())
            size.setValue(Utils.getBytes(salmonFile.getSize(),2));
        else {
            int items = salmonFile.listFiles().length;
            size.setValue(items + " item" + (items == 1?"":"s"));
        }
        String ext = SalmonDriveManager.getDrive().getExtensionFromFileName(name.getValue()).toLowerCase();
        type.setValue(ext);

        path.setValue(salmonFile.getPath());
    }

    public SalmonFile getSalmonFile() {
        return salmonFile;
    }

    public void setName(String name) throws Exception {
        salmonFile.rename(name, null);
        this.name.set(name);
    }

    @Override
    boolean isDirectory() {
        return salmonFile.isDirectory();
    }

    @Override
    public String getBaseName() throws Exception {
        return salmonFile.getBaseName();
    }

    @Override
    public ImageView getImage() {
        String icon = salmonFile.isFile()?"/icons/file-small.png":"/icons/folder-small.png";
        Image image = new Image(this.getClass().getResourceAsStream(icon));
        ImageView imageView = new ImageView(image);
        return imageView;
    }

    @Override
    public FileItem[] listFiles() throws Exception {
        SalmonFile[] files = salmonFile.listFiles();
        SalmonFileItem[] nfiles = new SalmonFileItem[files.length];
        int count = 0;
        for(SalmonFile file : files)
            nfiles[count++] = new SalmonFileItem(file);
        return nfiles;
    }

    @Override
    public long getFileSize() throws Exception {
        return salmonFile.getSize();
    }

    @Override
    public Object getTag() {
        return salmonFile.getTag();
    }

    @Override
    public void createDirectory(String folderName, byte[] key, byte[] dirNameNonce) throws Exception {
        salmonFile.createDirectory(folderName, key, dirNameNonce);
    }

    @Override
    public long getLastDateTimeModified() {
        return salmonFile.getLastDateTimeModified();
    }

    @Override
    public void delete() {
        salmonFile.delete();
    }

    @Override
    public void rename(String newValue) throws Exception {
        salmonFile.rename(newValue);
        name.setValue(salmonFile.getBaseName());
    }
}
