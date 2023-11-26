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
using Android.App;
using Android.Content.PM;
using Android.Content.Res;
using Android.Graphics;
using Android.Media;
using Android.OS;
using Android.Views;
using Android.Widget;
using AndroidX.AppCompat.App;
using Java.Util;
using Mku.Android.SalmonFS.Media;
using Mku.SalmonFS;
using Salmon.Vault.Extensions;
using System;
using System.Threading.Tasks;
using Button = Android.Widget.Button;
using ImageButton = Android.Widget.ImageButton;
using Timer = Java.Util.Timer;
using View = Android.Views.View;
using Salmon.Vault.DotNetAndroid;

namespace Salmon.Vault.Main;

[Activity(Label = "@string/app_name", Theme = "@style/AppTheme",
    ConfigurationChanges = ConfigChanges.ScreenSize | ConfigChanges.Orientation)]
public class MediaPlayerActivity : AppCompatActivity, ISurfaceHolderCallback
{
    private static readonly string TAG = nameof(MediaPlayerActivity);

    private static readonly int MEDIA_BUFFERS = 4;

    // make sure we use a large enough buffer for the MediaDataSource since some videos stall
    private static readonly int MEDIA_BUFFER_SIZE = 4 * 1024 * 1024;

    private static readonly int MEDIA_BACKOFFSET = 256 * 1024;

    // increase the threads if you have more cpus available for parallel processing
    private static readonly int MEDIA_THREADS = 2;

    private static readonly int THRESHOLD_SEEK = 30;

    private static SalmonFile[] videos;
    private static int pos;

    private readonly object swipeObj = new object();
    private readonly object mediaPlayerLock = new object();

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
    private bool looping;
    private float speed = 1.0f;
    private int old_x = 0;

    public static void SetMediaFiles(int position, SalmonFile[] mediaFiles)
    {
        pos = position;
        videos = mediaFiles;
    }

    override
    protected void OnCreate(Bundle bundle)
    {
        base.OnCreate(bundle);
        SetupWindow();
        SetupControls();
        SetupSurface();
        SetupMediaPlayer();
    }

    private void SetupControls()
    {
        mPlay.Click += (s, e) => TogglePlay();
        mLoop.Click += (s, e) => ToggleLoop();
        mSpeed.Click += (s, e) => ToggleSpeed();
        mFwd.Click += (s, e) => PlayNext();
        mRew.Click += (s, e) => PlayPrevious();
    }

    private void PlayNext()
    {
        if (pos <= videos.Length)
        {
            pos++;
            LoadContentAsync();
        }
    }

    private void PlayPrevious()
    {
        if (pos > 0)
        {
            pos--;
            LoadContentAsync();
        }
    }

    private void SetupWindow()
    {
        Window.SetFlags(WindowManagerFlags.Secure, WindowManagerFlags.Secure);
        SetContentView(Resource.Layout.mediaplayer);
        mSurfaceView = (SurfaceView)FindViewById(Resource.Id.surfaceview);
        mSurfaceView.Holder.AddCallback(this);
        mSeekBar = (SeekBar)FindViewById(Resource.Id.seekbar);
        mSeekBar.SetOnSeekBarChangeListener(new OnSeekBarChangeListener(this));
        mSeekBarLayout = (RelativeLayout)FindViewById(Resource.Id.seekbar_layout);
        mSeekBarLayout.Visibility = ViewStates.Gone;
        mTitleLayout = (RelativeLayout)FindViewById(Resource.Id.title_layout);
        mTitleLayout.Visibility = ViewStates.Gone;
        mTitle = (TextView)FindViewById(Resource.Id.title);
        mPlay = (ImageButton)FindViewById(Resource.Id.play);
        mLoop = (ImageButton)FindViewById(Resource.Id.loop);
        mSpeed = (Button)FindViewById(Resource.Id.speed);
        mRew = (ImageButton)FindViewById(Resource.Id.rew);
        mFwd = (ImageButton)FindViewById(Resource.Id.fwd);
    }

    private void SetupSurface()
    {
        mSurfaceView.SetOnTouchListener(new OnSurfaceTouchListener(this));
    }

    private void SetupMediaPlayer()
    {
        try
        {
            mediaPlayer = new MediaPlayer();
            gestureDetector = new GestureDetector(this, new MediaGestureListener(this));
            LoadContent();
        }
        catch (Exception ex)
        {
            ex.PrintStackTrace();
            Toast.MakeText(this, "Error: " + ex.Message, ToastLength.Long).Show();
        }
    }
    private void LoadContentAsync()
    {
        Task.Run(() =>
        {
            try
            {
                LoadContent();
            }
            catch (Exception e)
            {
                Console.Error.WriteLine(e);
            }
        });
    }

    private void LoadContent()
    {
        if (mediaPlayer.IsPlaying)
        {
            mediaPlayer.Stop();
        }
        mediaPlayer.Reset();
        mTitle.Text = videos[pos].BaseName;
        source = new SalmonMediaDataSource(this, videos[pos], MEDIA_BUFFERS, MEDIA_BUFFER_SIZE, MEDIA_THREADS, MEDIA_BACKOFFSET);
        mediaPlayer.SetDataSource(source);
        mediaPlayer.Prepared += (s, e) =>
        {
            Resize(0);
            Start();
        };
        mediaPlayer.PrepareAsync();
    }

    private class MediaPlayerTimerTask : TimerTask
    {
        MediaPlayerActivity activity;
        public MediaPlayerTimerTask(MediaPlayerActivity activity)
        {
            this.activity = activity;
        }
        override
        public void Run()
        {
            if (activity.mediaPlayer.IsPlaying)
                activity.mSeekBar.Progress = (int)(activity.mediaPlayer.CurrentPosition / (float)activity.mediaPlayer.Duration * 100);
        }
    }

    override
        public void OnConfigurationChanged(Configuration configuration)
    {
        base.OnConfigurationChanged(configuration);
        Resize(1000);
    }

    private void FitToWindow()
    {
        RelativeLayout parent = (RelativeLayout)mSurfaceView.Parent;
        if (parent.Width == 0 || parent.Height == 0)
            return;
        ViewGroup.LayoutParams layoutParams = mSurfaceView.LayoutParameters;
        if (mediaPlayer.VideoWidth / (float)mediaPlayer.VideoHeight > parent.Width / (float)parent.Height)
        {
            layoutParams.Width = parent.Width;
            layoutParams.Height = (int)(parent.Width / (float)mediaPlayer.VideoWidth * mediaPlayer.VideoHeight);
        }
        else
        {
            layoutParams.Height = parent.Height;
            layoutParams.Width = (int)(parent.Height / (float)mediaPlayer.VideoHeight * mediaPlayer.VideoWidth);
        }
        mSurfaceView.LayoutParameters = layoutParams;
    }

    override
    protected void OnDestroy()
    {
        if (mediaPlayer != null)
        {
            mediaPlayer.Stop();
        }
        if (source != null)
        {
            try
            {
                source.Close();
            }
            catch (Exception e)
            {
                e.PrintStackTrace();
            }
        }
        source = null;
        if (mediaPlayer != null)
        {
            mediaPlayer.SetDisplay(null);
            mediaPlayer.SetSurface(null);
            mediaPlayer.Release();
        }
        mediaPlayer = null;
        base.OnDestroy();
    }

    override
    protected void OnPause()
    {
        base.OnPause();
        Pause();
    }

    private void Start()
    {
        mediaPlayer.Start();
        mediaPlayer.Looping = looping;
        UpdateSpeed();
        if (timer != null)
        {
            timer.Cancel();
        }
        TimerTask timerTask = new MediaPlayerTimerTask(this);
        timer = new Timer();
        timer.Schedule(timerTask, 1000, 1000);
        UpdateControls();
    }

    private void UpdateControls()
    {
        if (mediaPlayer.IsPlaying)
            mPlay.SetImageResource(Android.Resource.Drawable.IcMediaPause);
        else
            mPlay.SetImageResource(Android.Resource.Drawable.IcMediaPlay);
    }

    private void Pause()
    {
        if (mediaPlayer != null)
        {
            mediaPlayer.Pause();
            if (timer != null)
            {
                timer.Cancel();
            }
        }
        UpdateControls();
    }

    private void TogglePlay()
    {
        if (mediaPlayer != null)
        {
            if (mediaPlayer.IsPlaying)
            {
                Pause();
            }
            else
            {
                Start();
            }
        }
    }

    private void ToggleLoop()
    {
        if (mediaPlayer != null)
        {
            looping = !looping;
            mediaPlayer.Looping = looping;
            RunOnUiThread(() =>
            {
                if (mediaPlayer.Looping)
                    mLoop.SetImageResource(Android.Resource.Drawable.IcMenuRevert);
                else
                    mLoop.SetImageResource(Android.Resource.Drawable.IcMenuRotate);
            });
        }
    }


    private void ToggleSpeed()
    {
        lock (mediaPlayerLock)
        {
            if (mediaPlayer != null)
            {
                speed -= 0.10f;
                if (speed < 0.10f)
                    speed = 1.0f;
                UpdateSpeed();
                RunOnUiThread(() =>
                {
                    if (speed != 1.0f)
                        mSpeed.Text = "." + ((int)(speed * 10)) + "x";
                    else
                        mSpeed.Text = "1x";
                });
            }
        }
    }

    private void UpdateSpeed()
    {
        bool isPlaying = mediaPlayer.IsPlaying;
        try
        {
            mediaPlayer.PlaybackParams = mediaPlayer.PlaybackParams.SetSpeed(speed);
            if (!isPlaying)
            {
                mediaPlayer.Pause();
            }
        }
        catch (Exception ignore) { }
    }

    override
    public bool OnTouchEvent(MotionEvent e)
    {
        Resize(0);
        return gestureDetector.OnTouchEvent(e);
    }

    private void Resize(int delay)
    {
        new Handler(Looper.MainLooper).PostDelayed(() => FitToWindow(), delay);
    }

    private void ToggleSeekBar()
    {
        if (mSeekBarLayout.Visibility == ViewStates.Gone)
        {
            mTitleLayout.Visibility = ViewStates.Visible;
            mSeekBarLayout.Visibility = ViewStates.Visible;
        }
        else
        {
            mTitleLayout.Visibility = ViewStates.Gone;
            mSeekBarLayout.Visibility = ViewStates.Gone;
        }
    }

    public void SurfaceChanged(ISurfaceHolder holder, Format format, int width, int height)
    {

    }


    public void SurfaceCreated(ISurfaceHolder holder)
    {
        mediaPlayer.SetDisplay(holder);
    }

    public void SurfaceDestroyed(ISurfaceHolder holder)
    {

    }

    private void OnFingerMove(MotionEvent motionevent)
    {
        int x_delta = (int)motionevent.GetX() - old_x;
        if (motionevent.Action == MotionEventActions.Down)
        {
            old_x = (int)motionevent.GetX();
        }
        else if (motionevent.Action == MotionEventActions.Up)
        {
            if (Math.Abs(x_delta) > THRESHOLD_SEEK)
            {
                OnSwipe(x_delta);
            }
        }
    }

    private void OnSwipe(int interval)
    {
        lock (swipeObj)
        {
            if (mediaPlayer != null)
                SeekDelta(100 * interval);
        }
    }

    public void SeekDelta(int i)
    {
        int pos = mediaPlayer.CurrentPosition + i;
        mediaPlayer.SeekTo(pos);

    }

    private class OnSeekBarChangeListener : Java.Lang.Object, SeekBar.IOnSeekBarChangeListener
    {
        MediaPlayerActivity activity;
        public OnSeekBarChangeListener(MediaPlayerActivity activity)
        {
            this.activity = activity;
        }

        public void OnProgressChanged(SeekBar seekBar, int progress, bool fromUser)
        {
            if (fromUser)
            {
                activity.mediaPlayer.SeekTo((int)(progress / (float)100.00 * activity.mediaPlayer.Duration));
            }
        }


        public void OnStartTrackingTouch(SeekBar seekBar)
        {

        }


        public void OnStopTrackingTouch(SeekBar seekBar)
        {

        }
    }

    private class MediaGestureListener : GestureDetector.SimpleOnGestureListener
    {
        MediaPlayerActivity activity;

        public MediaGestureListener(MediaPlayerActivity activity)
        {
            this.activity = activity;
        }

        override
        public bool OnDown(MotionEvent e)
        {
            return true;
        }

        override
        public bool OnSingleTapConfirmed(MotionEvent e)
        {
            activity.ToggleSeekBar();
            return base.OnSingleTapConfirmed(e);
        }

        override
        public bool OnDoubleTap(MotionEvent e)
        {
            activity.TogglePlay();
            return true;
        }
    }

    class OnSurfaceTouchListener : Java.Lang.Object, View.IOnTouchListener
    {
        private readonly MediaPlayerActivity activity;
        public OnSurfaceTouchListener(MediaPlayerActivity activity)
        {
            this.activity = activity;
        }

        public bool OnTouch(View view, MotionEvent motionevent)
        {
            activity.OnFingerMove(motionevent);
            activity.gestureDetector.OnTouchEvent(motionevent);
            return true;
        }
    }
}