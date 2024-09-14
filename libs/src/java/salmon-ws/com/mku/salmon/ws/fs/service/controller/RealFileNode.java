package com.mku.salmon.ws.fs.service.controller;

import com.mku.salmon.SalmonFile;

import java.io.IOException;

public class RealFileNode {
    private transient SalmonFile file;

    public RealFileNode(SalmonFile file) {
        this.file = file;
    }

    public String getPath() throws IOException {
        return this.file.getPath();
    }
}
