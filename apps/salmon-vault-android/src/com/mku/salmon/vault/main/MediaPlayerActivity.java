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

import android.content.res.Configuration;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.mku.android.salmonfs.media.SalmonMediaDataSource;
import com.mku.salmon.vault.android.R;
import com.mku.salmon.vault.utils.WindowUtils;
import com.mku.salmonfs.SalmonFile;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

public class MediaPlayerActivity extends AppCompatActivity implements SurfaceHolder.Callback {
    private static final String TAG = MediaPlayerActivity.class.getName();

    private static final int MEDIA_BUFFERS = 4;

    // make sure we use a large enough buffer for the MediaDataSource since some videos stall
    private static final int MEDIA_BUFFER_SIZE = 4 * 1024 * 1024;

    private static final int MEDIA_BACKOFFSET = 256 * 1024;

    // increase the threads if you have more cpus available for parallel processing
    private static final int MEDIA_THREADS = 2;

    private static final int THRESHOLD_SEEK = 30;

    private static SalmonFile[] videos;
    private static int pos;

    private final Object swipeObj = new Object();
    private final Object mediaPlayerLock = new Object();

    private GestureDetector gestureDetector;
    private Timer timer;
    private SurfaceView mSurfaceView;
    private SeekBar mSeekBar;
    private MediaPlayer mediaPlayer;
    private SalmonMediaDataSource source;
    private RelativeLayout mSeekBarLayout;
    private RelativeLayout mTitleLayout;
    private TextView mTitle;
    private ImageButton mPlay;
    private ImageButton mLoop;
    private Button mSpeed;
    private ImageButton mRew;
    private ImageButton mFwd;
    private boolean looping;
    private float speed = 1.0f;
    private int old_x = 0;

    public static void setMediaFiles(int position, SalmonFile[] mediaFiles) {
        pos = position;
        videos = mediaFiles;
    }

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setupWindow();
        setupControls();
        setupSurface();
        setupMediaPlayer();
    }

    private void setupControls() {
        mPlay.setOnClickListener((View view) -> togglePlay());
        mLoop.setOnClickListener((View view) -> toggleLoop());
        mSpeed.setOnClickListener((View view) -> toggleSpeed());
        mFwd.setOnClickListener((View view) -> playNext());
        mRew.setOnClickListener((View view) -> playPrevious());
    }

    protected void playNext() {
        if (pos <= videos.length) {
            pos++;
            loadContentAsync();
        }
    }

    protected void playPrevious() {
        if (pos > 0) {
            pos--;
            loadContentAsync();
        }
    }

    private void setupWindow() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.mediaplayer);
        mSurfaceView = findViewById(R.id.surfaceview);
        mSurfaceView.getHolder().addCallback(this);
        mSeekBar = findViewById(R.id.seekbar);
        mSeekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener());
        mSeekBarLayout = findViewById(R.id.seekbar_layout);
        mSeekBarLayout.setVisibility(View.GONE);
        mTitleLayout = findViewById(R.id.title_layout);
        mTitleLayout.setVisibility(View.GONE);
        mTitle = findViewById(R.id.title);
        mPlay = findViewById(R.id.play);
        mLoop = findViewById(R.id.loop);
        mSpeed = findViewById(R.id.speed);
        mRew = findViewById(R.id.rew);
        mFwd = findViewById(R.id.fwd);
    }

    private void setupSurface() {
        mSurfaceView.setOnTouchListener(new OnSurfaceTouchListener(this));
    }

    private void setupMediaPlayer() {
        try {
            mediaPlayer = new MediaPlayer();
            gestureDetector = new GestureDetector(this, new MediaGestureListener(this));
            loadContent(videos[pos]);
        } catch (Exception ex) {
            ex.printStackTrace();
            Toast.makeText(this, "Error: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void loadContentAsync() {
        new Thread(() -> {
            try {
                loadContent(videos[pos]);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    protected void loadContent(SalmonFile file) throws Exception {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }
        mediaPlayer.reset();
        mTitle.setText(videos[pos].getBaseName());
        source = new SalmonMediaDataSource(this, file, MEDIA_BUFFERS, MEDIA_BUFFER_SIZE, MEDIA_THREADS, MEDIA_BACKOFFSET);
        mediaPlayer.setDataSource(source);
        mediaPlayer.setOnPreparedListener((MediaPlayer mp) -> {
            resize(0);
            start();
        });
        mediaPlayer.prepareAsync();
    }

    private class MediaPlayerTimerTask extends TimerTask {
        @Override
        public void run() {
            try {
                if (mediaPlayer != null && mediaPlayer.isPlaying())
                    mSeekBar.setProgress((int) (mediaPlayer.getCurrentPosition() / (float) mediaPlayer.getDuration() * 100));
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration configuration) {
        super.onConfigurationChanged(configuration);
        resize(1000);
    }

    private void fitToWindow() {
        RelativeLayout parent = (RelativeLayout) mSurfaceView.getParent();
        if (parent.getWidth() == 0 || parent.getHeight() == 0)
            return;
        ViewGroup.LayoutParams layoutParams = mSurfaceView.getLayoutParams();
        if (mediaPlayer.getVideoWidth() / (float) mediaPlayer.getVideoHeight() > parent.getWidth() / (float) parent.getHeight()) {
            layoutParams.width = parent.getWidth();
            layoutParams.height = (int) (parent.getWidth() / (float) mediaPlayer.getVideoWidth() * mediaPlayer.getVideoHeight());
        } else {
            layoutParams.height = parent.getHeight();
            layoutParams.width = (int) (parent.getHeight() / (float) mediaPlayer.getVideoHeight() * mediaPlayer.getVideoWidth());
        }
        mSurfaceView.setLayoutParams(layoutParams);
    }

    @Override
    protected void onDestroy() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
        }
        if (source != null) {
            try {
                source.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        source = null;
        if (mediaPlayer != null) {
            mediaPlayer.setDisplay(null);
            mediaPlayer.setSurface(null);
            mediaPlayer.release();
        }
        mediaPlayer = null;
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        pause();
    }

    private void start() {
        mediaPlayer.start();
        mediaPlayer.setLooping(looping);
        if (timer != null) {
            timer.cancel();
        }
        TimerTask timerTask = new MediaPlayerTimerTask();
        timer = new Timer();
        timer.schedule(timerTask, 1000, 1000);
        updateControls();
    }

    private void updateControls() {
        if (mediaPlayer.isPlaying())
            mPlay.setImageResource(android.R.drawable.ic_media_pause);
        else
            mPlay.setImageResource(android.R.drawable.ic_media_play);
    }

    private void pause() {
        if (mediaPlayer != null) {
            mediaPlayer.pause();
            if (timer != null) {
                timer.cancel();
            }
        }
        updateControls();
    }

    private void togglePlay() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                pause();
            } else {
                start();
            }
        }
    }

    private void toggleLoop() {
        if (mediaPlayer != null) {
            looping = !looping;
            mediaPlayer.setLooping(looping);
            runOnUiThread(() ->
            {
                if (mediaPlayer.isLooping())
                    mLoop.setImageResource(android.R.drawable.ic_menu_revert);
                else
                    mLoop.setImageResource(android.R.drawable.ic_menu_rotate);
            });
        }
    }

    private void toggleSpeed() {
        synchronized (mediaPlayerLock) {
            if (mediaPlayer != null) {
                speed -= 0.10f;
                if (speed <= 0.10f)
                    speed = 1.0f;
                updateSpeed();
                runOnUiThread(() ->
                {
                    if (speed != 1.0f)
                        mSpeed.setText("." + ((int) (speed * 10)) + "x");
                    else
                        mSpeed.setText("1x");
                });
            }
        }
    }

    private void updateSpeed() {
        boolean isPlaying = mediaPlayer.isPlaying();
        try {
            mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed(speed));
            if (!isPlaying) {
                mediaPlayer.pause();
            }
        } catch (Exception ignore) {
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        resize(0);
        return gestureDetector.onTouchEvent(e);
    }

    private void resize(int delay) {
        new Handler(Looper.getMainLooper()).postDelayed(this::fitToWindow, delay);
    }

    private void toggleSeekBar() {
        if (mSeekBarLayout.getVisibility() == View.GONE) {
            mTitleLayout.setVisibility(View.VISIBLE);
            mSeekBarLayout.setVisibility(View.VISIBLE);
        } else {
            mTitleLayout.setVisibility(View.GONE);
            mSeekBarLayout.setVisibility(View.GONE);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mediaPlayer.setDisplay(holder);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    }

    private void onFingerMove(MotionEvent motionevent) {
        int x_delta = (int) motionevent.getX() - old_x;
        if (motionevent.getAction() == MotionEvent.ACTION_DOWN) {
            old_x = (int) motionevent.getX();
        } else if (motionevent.getAction() == MotionEvent.ACTION_UP) {
            if (Math.abs(x_delta) > THRESHOLD_SEEK) {
                onSwipe(x_delta);
            }
        }
    }

    private void onSwipe(int interval) {
        synchronized (swipeObj) {
            if (mediaPlayer != null)
                seekDelta(100 * interval);
        }
    }

    public void seekDelta(int i) {
        int pos = mediaPlayer.getCurrentPosition() + i;
        mediaPlayer.seekTo(pos);

    }

    private class OnSeekBarChangeListener implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser) {
                mediaPlayer.seekTo((int) (progress / (float) 100.00 * mediaPlayer.getDuration()));
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }

    private class MediaGestureListener extends GestureDetector.SimpleOnGestureListener {
        MediaPlayerActivity activity;

        public MediaGestureListener(MediaPlayerActivity activity) {
            this.activity = activity;
        }

        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            activity.toggleSeekBar();
            return super.onSingleTapConfirmed(e);
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            return MediaPlayerActivity.this.onDoubleTap(e);
        }

        @Override
        public void onLongPress(MotionEvent e) {
            super.onLongPress(e);
            MediaPlayerActivity.this.onLongPress(e);
        }

    }

    protected boolean onDoubleTap(MotionEvent e) {
        togglePlay();
        return true;
    }

    protected void onLongPress(MotionEvent e) {

    }

    static class OnSurfaceTouchListener implements View.OnTouchListener {
        private final MediaPlayerActivity activity;

        public OnSurfaceTouchListener(MediaPlayerActivity activity) {
            this.activity = activity;
        }

        @Override
        public boolean onTouch(View view, MotionEvent motionevent) {
            activity.onFingerMove(motionevent);
            activity.gestureDetector.onTouchEvent(motionevent);
            return true;
        }
    }
}
