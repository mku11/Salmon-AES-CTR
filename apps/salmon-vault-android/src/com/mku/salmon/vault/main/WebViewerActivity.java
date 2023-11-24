package com.mku.salmon.vault.main;
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

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.mku.salmon.vault.android.R;
import com.mku.io.InputStreamWrapper;
import com.mku.salmon.integrity.SalmonIntegrityException;
import com.mku.salmon.io.SalmonStream;
import com.mku.salmon.vault.dialog.SalmonDialog;
import com.mku.salmonfs.SalmonFile;
import com.mku.utils.SalmonFileUtils;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedInputStream;
import java.io.IOException;

public class WebViewerActivity extends AppCompatActivity {
    private static final String TAG = WebViewerActivity.class.getName();
    private static final int SWIPE_DISTANCE_THRESHOLD = 100;
    private static final int SWIPE_VELOCITY_THRESHOLD = 1200;
    private static final int ENC_BUFFER_SIZE = 512 * 1024;

    private static SalmonFile[] fileList = null;
    private static int pos;

    public WebView webView;
    private SalmonWebViewClient webViewClient;
    private BufferedInputStream stream;
    private TextView mTitle;
    private final Object swipeObj = new Object();

    public static void setContentFiles(int position, SalmonFile[] salmonFiles) {
        pos = position;
        fileList = salmonFiles;
    }

    public void loadContentAsync() {
        new Thread(() -> {
            try {
                loadContent();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void setupWebViewClient() {
        webViewClient = new SalmonWebViewClient();
        webView.setWebViewClient(webViewClient);

    }

    protected void loadContent() throws Exception {
        String filename = fileList[pos].getBaseName();
        String ext = SalmonFileUtils.getExtensionFromFileName(filename).toLowerCase();
        String mimeType = null;
        try {
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        if (mimeType == null || mimeType.trim().equals("")) {
            mimeType = "text/plain";
        }

        try {
            SalmonStream encStream = fileList[pos].getInputStream();

            // in order for the webview not to crash we suppress Exceptions
            encStream.setFailSilently(true);
            InputStreamWrapper inputStream = new InputStreamWrapper(encStream);

            // we inject our SalmonStream into the webview client
            stream = new BufferedInputStream(inputStream, ENC_BUFFER_SIZE);
            webViewClient.setStream(mimeType, stream);
            runOnUiThread(() -> {
                mTitle.setText(filename);
                webView.loadUrl("file:android_asset/imagedata.dat");
            });
        } catch (SalmonIntegrityException ex) {
            ex.printStackTrace();
            runOnUiThread(() -> Toast.makeText(WebViewerActivity.this, getString(R.string.FileCorruptOrTampered), Toast.LENGTH_LONG).show());
        } catch (Exception ex) {
            ex.printStackTrace();
            runOnUiThread(() -> Toast.makeText(WebViewerActivity.this, "Error: " + ex.getMessage(), Toast.LENGTH_LONG).show());
        }

    }

    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        loadContentAsync();
    }

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setupWindow();
        resetScreen();
        init();
        loadContentAsync();
    }

    private void promptTextSearch() {
        SalmonDialog.promptEdit(getString(R.string.Search), "Keywords", (String value, Boolean checked) -> {
            webView.findAllAsync(value);
        }, "", false, false, false, getString(R.string.MatchAnyTerm));
    }

    private void setupWindow() {
        if (SettingsActivity.getHideScreenContents())
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
    }

    protected void onDestroy() {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        stream = null;
        super.onDestroy();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();
        menu.add(0, ActionType.SEARCH.ordinal(), 0, getString(R.string.Search))
                .setIcon(android.R.drawable.ic_menu_search)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        if (ActionType.values()[item.getItemId()] == ActionType.SEARCH) {
            promptTextSearch();
            return true;
        }
        return false;
    }

    private void init() {
        setContentView(R.layout.webviewer);
        webView = findViewById(R.id.webview);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        mTitle = findViewById(R.id.title);

        initWebView();
        initGestures();
        setupWebViewClient();
        setupActionBar();
    }

    private void setupActionBar() {
        try {
            if (fileList.length > 0 && !SalmonFileUtils.isText(fileList[0].getBaseName())) {
                if (getSupportActionBar() != null)
                    getSupportActionBar().hide();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initGestures() {
        final GestureDetector gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                return false;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                super.onLongPress(e);

            }

            @Override
            public boolean onDoubleTapEvent(MotionEvent e) {
                return false;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                return false;
            }

            @Override
            public boolean onDown(MotionEvent e) {
                return false;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                float distanceX = e2.getX() - e1.getX();
                float distanceY = e2.getY() - e1.getY();
                if (Math.abs(distanceX) > Math.abs(distanceY)
                        && Math.abs(distanceX) > SWIPE_DISTANCE_THRESHOLD
                        && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    onSwipe((int) (-1 * distanceX));
                    return true;
                }
                return false;
            }
        });
        webView.setOnTouchListener((v, motionevent) -> {
            gd.onTouchEvent(motionevent);
            return false;
        });
    }

    private void onSwipe(int interval) {
        synchronized (swipeObj) {
            if (interval > 0) {
                playNextItem();
            } else if (interval < 0) {
                playPreviousItem();
            }
        }
    }

    private synchronized void playPreviousItem() {
        if (pos > 0) {
            pos--;
            loadContentAsync();
        }
    }

    private synchronized void playNextItem() {
        if (pos < fileList.length - 1) {
            pos++;
            loadContentAsync();
        }
    }

    private void initWebView() {
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false); // old zoom buttons
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setSupportMultipleWindows(true);
        webView.getSettings().setBlockNetworkImage(true);
    }

    public void onConfigurationChanged(@NotNull Configuration c) {
        super.onConfigurationChanged(c);
        try {
            loadContent();
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    public void resetScreen() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private static class SalmonWebViewClient extends WebViewClient {
        private BufferedInputStream stream;
        private String mimeType;

        public void setStream(String mimeType, BufferedInputStream stream) {
            this.stream = stream;
            this.mimeType = mimeType;
        }

        public WebResourceResponse
        shouldInterceptRequest(WebView view, WebResourceRequest request) {
            WebResourceResponse res = new WebResourceResponse(mimeType, "UTF-8", stream);
            if (res == null)
                return super.shouldInterceptRequest(view, request);
            return res;

        }
    }
}
