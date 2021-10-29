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
using Android.Runtime;
using Android.Views;
using Android.Widget;
using AndroidX.AppCompat.App;
using Salmon.Droid.Media;
using Salmon.FS;
using System;
using System.Timers;
using static Android.Views.ViewGroup;

namespace Salmon.Droid.Main
{
    [Activity(Label = "@string/app_name", Icon = "@drawable/logo", Theme = "@style/Theme.AppCompat.NoActionBar",
        ConfigurationChanges = ConfigChanges.ScreenSize | ConfigChanges.Orientation)]
    public partial class MediaPlayerActivity : AppCompatActivity, ISurfaceHolderCallback
    {
        // make sure we use a large enough buffer for the MediaDataSource
        // since some videos stall
        private static readonly int ENC_MEDIA_CACHE_BUFFER_SIZE = 2 * 1024 * 1024;
        // increase the threads if you have more cpus available for parallel processing
        private static readonly int ENC_MEDIA_THREADS = 2;
        private SurfaceView mSurfaceView;
        private SeekBar mSeekBar;
        private MediaPlayer mediaPlayer;
        private SalmonMediaDataSource source;
        private static SalmonFile mediaCryptoFile;
        GestureDetector gestureDetector;
        Timer timer;
        private RelativeLayout mSeekBarLayout;
        private RelativeLayout mTitleLayout;

        public static void SetMediaFile(SalmonFile mediaFile)
        {
            mediaCryptoFile = mediaFile;
        }

        protected override void OnCreate(Bundle bundle)
        {
            base.OnCreate(bundle);
            Window.SetFlags(WindowManagerFlags.Fullscreen, WindowManagerFlags.Fullscreen);
            SetContentView(Resource.Layout.mediaplayer);
            mSurfaceView = (SurfaceView)FindViewById(Resource.Id.surfaceview);
            mSurfaceView.Holder.AddCallback(this);
            mSeekBar = (SeekBar)FindViewById(Resource.Id.seekbar);
            mSeekBar.ProgressChanged += MSeekBar_ProgressChanged;
            mSeekBarLayout = (RelativeLayout)FindViewById(Resource.Id.seekbar_layout);
            mSeekBarLayout.Visibility = ViewStates.Gone;
            mTitleLayout = (RelativeLayout)FindViewById(Resource.Id.title_layout);
            mTitleLayout.Visibility = ViewStates.Gone;
            SetupMediaPlayer();
        }


        private void SetupMediaPlayer()
        {
            try
            {
                mediaPlayer = new MediaPlayer();
                gestureDetector = new GestureDetector(this, new MediaGestureListener(this));
                timer = new Timer(1000);
                timer.Elapsed += Timer_Elapsed;
                source = new SalmonMediaDataSource(this, mediaCryptoFile, ENC_MEDIA_CACHE_BUFFER_SIZE, ENC_MEDIA_THREADS);
                mediaPlayer.SetDataSource(source);
                mediaPlayer.Prepare();
                Start();
            }
            catch (Exception ex)
            {
                Toast.MakeText(this, "Error: " + ex.Message, ToastLength.Long).Show();
            }
        }

        private void Timer_Elapsed(object sender, ElapsedEventArgs e)
        {
            mSeekBar.Progress = (int) (mediaPlayer.CurrentPosition / (float) mediaPlayer.Duration * 100);
        }

        

        public override void OnConfigurationChanged(Configuration configuration)
        {
            base.OnConfigurationChanged(configuration);
            new Handler(Looper.MainLooper).PostDelayed(() =>
            {
                FitToWindow();
            }, 1000);

        }

        private void FitToWindow()
        {
            RelativeLayout parent = (RelativeLayout)mSurfaceView.Parent;
            if (parent.Width == 0 || parent.Height == 0)
                return;
            LayoutParams layoutParams = mSurfaceView.LayoutParameters;
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

        protected override void OnDestroy()
        {
            if (mediaPlayer != null)
            {
                mediaPlayer.Stop();
            }
            if(source != null)
                source.Close();
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

        protected override void OnPause()
        {
            base.OnPause();
            Pause();
        }

        private void Start()
        {
            mediaPlayer.Start();
            timer.Start();
        }

        private void Pause()
        {
            if (mediaPlayer != null)
            {
                mediaPlayer.Pause();
                timer.Stop();
            }
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

        public override bool OnTouchEvent(MotionEvent e)
        {
            return gestureDetector.OnTouchEvent(e);
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

        private void MSeekBar_ProgressChanged(object sender, SeekBar.ProgressChangedEventArgs e)
        {
            if (e.FromUser)
            {
                mediaPlayer.SeekTo((int)(e.Progress / (float)100.00 * mediaPlayer.Duration));
            }
        }

        public void SurfaceChanged(ISurfaceHolder holder, [GeneratedEnum] Format format, int width, int height)
        {
            mediaPlayer.SetDisplay(holder);
        }

        public void SurfaceCreated(ISurfaceHolder holder)
        {
            FitToWindow();
        }

        public void SurfaceDestroyed(ISurfaceHolder holder)
        {

        }

        private class MediaGestureListener : GestureDetector.SimpleOnGestureListener
        {
            private MediaPlayer mediaPlayer;
            MediaPlayerActivity activity;

            public MediaGestureListener(MediaPlayerActivity activity)
            {
                this.activity = activity;
            }

            public override bool OnDown(MotionEvent e)
            {
                return true;
            }

            public override bool OnSingleTapConfirmed(MotionEvent e)
            {
                activity.ToggleSeekBar();
                return base.OnSingleTapConfirmed(e);
            }

            public override bool OnDoubleTap(MotionEvent e)
            {
                activity.TogglePlay();
                return true;
            }
        }
    }
}