package com.mku.salmon.vault.model;
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

import com.mku.func.BiConsumer;
import com.mku.salmon.SalmonSecurityException;
import com.mku.salmon.integrity.SalmonIntegrityException;
import com.mku.salmon.vault.dialog.SalmonDialog;
import com.mku.salmon.vault.services.IWebBrowserService;
import com.mku.salmon.vault.services.ServiceLocator;
import com.mku.salmon.vault.utils.IPropertyNotifier;
import com.mku.salmonfs.SalmonAuthException;
import com.mku.salmonfs.SalmonFile;
import com.mku.salmonfs.SalmonFileInputStream;
import com.mku.utils.SalmonFileUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;

public class SalmonContentViewer implements IPropertyNotifier {
    private static final String URL = "https://localhost/";
    private static final int BUFFERS = 4;
    private static final int BUFFER_SIZE = 4 * 1024 * 1024;
    private static final int THREADS = 4;
    private static final int BACK_OFFSET = 256 * 1024;
    private final IWebBrowserService webBrowserService;
    private InputStream stream;

    public String _source;
    private HashSet<BiConsumer<Object, String>> observers = new HashSet<>();

    private String getSource() {
        return _source;
    }

    public void setSource(String value) {

        if (_source != value) {
            _source = value;
            propertyChanged(this, "Source");
        }
    }

    public SalmonContentViewer() {
        webBrowserService = ServiceLocator.getInstance().resolve(IWebBrowserService.class);
    }

    public void OnClose() {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        unobservePropertyChanges();
    }

    void Load(SalmonFile file) throws SalmonSecurityException, SalmonIntegrityException, IOException, SalmonAuthException {
        String filePath = null;
        try {
            filePath = file.getRealPath();
        } catch (Exception e) {
            e.printStackTrace();
        }
        String filename = file.getBaseName();
        String mimeType = null; //= MimeTypesMap.GetMimeType(filename);
        // webview2 buffering with partial content works only with video and audio
        boolean buffered = SalmonFileUtils.isVideo(filename) || SalmonFileUtils.isAudio(filename);
        String contentPath = "content.dat";
        webBrowserService.setResponse(URL + contentPath, mimeType, file.getSize(), BUFFER_SIZE, buffered, (pos) ->
        {
            try {
                if (stream != null)
                    stream.close();
                SalmonFileInputStream fileStream = new SalmonFileInputStream(file, BUFFERS, BUFFER_SIZE, THREADS, BACK_OFFSET);
                // we need to offset the start of the stream so the webview can see it as partial content
                if (buffered) {
                    fileStream.setPositionStart(pos);
                    fileStream.setPositionEnd(pos + BUFFER_SIZE - 1);
                    fileStream.skip(0);
                }
                stream = fileStream;
                return stream;
            } catch (Exception ex) {
                SalmonDialog.promptDialog("Error", "Could not load stream: " + ex.getMessage());
            }
            return null;
        });
        setSource(URL + contentPath);
    }

    @Override
    public HashSet<BiConsumer<Object, String>> getObservers() {
        return observers;
    }
}