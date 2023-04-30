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
using FFmpeg.AutoGen;
using Salmon.Alert;
using Salmon.FS;
using Salmon.Model;
using Salmon.Window;
using SalmonFS.Media;
using System;
using System.ComponentModel;
using System.IO;
using System.Runtime.InteropServices;
using System.Threading;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using Unosquare.FFME.Common;
using static Salmon.Alert.SalmonDialog;
using static System.Windows.Forms.VisualStyles.VisualStyleElement.Tab;
using MediaType = Salmon.ViewModel.MainViewModel.MediaType;

namespace Salmon.ViewModel
{

    public class MediaPlayerViewModel : INotifyPropertyChanged
    {
        private static readonly int MEDIA_BUFFER_SIZE = 4 * 1024 * 1024;
        private static readonly int MEDIA_THREADS = 4;
        private static readonly string FFMPEG_DIR = @"c:\ffmpeg\x64";

        private System.Windows.Window window;
        private FileItem item;

        private MediaType type;

        public ImageSource _playImageSource;
        public ImageSource PlayImageSource
        {
            get => _playImageSource;
            set
            {
                _playImageSource = value;
                if (PropertyChanged != null)
                    PropertyChanged(this, new PropertyChangedEventArgs("PlayImageSource"));
            }
        }

        public int _sliderValue;
        public int SliderValue
        {
            get => _sliderValue;
            set
            {
                if (_sliderValue != value)
                {
                    _sliderValue = value;
                    if (PropertyChanged != null)
                        PropertyChanged(this, new PropertyChangedEventArgs("SliderValue"));
                }
            }
        }

        public string _currTime;
        public string CurrTime
        {
            get => _currTime;
            set
            {
                _currTime = value;
                if (PropertyChanged != null)
                    PropertyChanged(this, new PropertyChangedEventArgs("CurrTime"));
            }
        }

        public string _totalTime;
        public string TotalTime
        {
            get => _totalTime;
            set
            {
                _totalTime = value;
                if (PropertyChanged != null)
                    PropertyChanged(this, new PropertyChangedEventArgs("TotalTime"));
            }
        }

        private bool quit = false;
        private Thread timer;

        ImageSource playImage = GetImageSource("/icons/play.png");
        ImageSource pauseImage = GetImageSource("/icons/pause.png");

        public event PropertyChangedEventHandler PropertyChanged;

        public static string dtFormat = "HH:mm:ss";


        public class RelayCommand<T> : ICommand
        {
            readonly Action<T> command;


            public RelayCommand(Action<T> command)
            {
                this.command = command;
            }

            public event EventHandler CanExecuteChanged;

            public bool CanExecute(object parameter)
            {
                return true;
            }

            public void Execute(object parameter)
            {
                if (command != null)
                {
                    command((T)parameter);
                }
            }

        }

        private ICommand _clickCommand;
        private SalmonMediaDataSource stream;
        private Unosquare.FFME.MediaElement mediaPlayer;

        public ICommand ClickCommand
        {
            get
            {
                if (_clickCommand == null)
                {
                    _clickCommand = new RelayCommand<ActionType>(OnCommandClicked);
                }
                return _clickCommand;
            }
        }
        private void OnCommandClicked(ActionType actionType)
        {
            switch (actionType)
            {
                case ActionType.PLAY:
                    TogglePlay();
                    break;
            }
        }

        public MediaPlayerViewModel()
        {
            PlayImageSource = playImage;
        }

        public static BitmapImage GetImageSource(string path)
        {
            BitmapImage imageSource = new BitmapImage(new Uri(path, UriKind.Relative));
            return imageSource;
        }

        public void SetWindow(System.Windows.Window window, System.Windows.Window owner)
        {
            this.window = window;
            this.window.Owner = owner;

            this.window.Closing += (object sender, CancelEventArgs e) =>
            {
                StopTimer();
                WindowUtils.RunOnMainThread(() =>
                {
                    Stop();
                    mediaPlayer.Close();
                    mediaPlayer = null;
                    if (stream != null)
                        stream.Close();

                });
            };
        }

        public static void OpenMediaPlayer(FileItem file, System.Windows.Window owner, MediaType type)
        {
            if (!Directory.Exists(FFMPEG_DIR) || !File.Exists(FFMPEG_DIR + "/ffmpeg.exe"))
            {
                WindowCommon.PromptDialog("Media Player",
                    "Could not find FFMPEG in " + FFMPEG_DIR + ". Download version "
                    + Config.FFMPEGLibraryVersion + " and copy the extract the contents of directory bin to directory " + FFMPEG_DIR,
                    "Download", () => URLUtils.GoToUrl(Config.FFMPEGLibraryURL),
                    "Cancel", null);
                return;
            }
            View.MediaPlayer mediaPlayer = new View.MediaPlayer();
            mediaPlayer.SetWindow(owner);
            mediaPlayer.Load(file, type);
            mediaPlayer.ShowDialog();
        }

        private void Play()
        {
            mediaPlayer.Play();
            PlayImageSource = pauseImage;
        }

        private void Pause()
        {
            mediaPlayer.Pause();
            PlayImageSource = playImage;
        }

        private void Stop()
        {
            mediaPlayer.Stop();
            PlayImageSource = playImage;
        }
        
        public void Load(FileItem fileItem, MediaType type)
        {
            item = fileItem;
            this.type = type;
            SalmonFile file = ((SalmonFileItem)item).GetSalmonFile();
            string filePath = null;
            try
            {
                filePath = file.GetRealPath();
            }
            catch (Exception e)
            {
                Console.Error.WriteLine(e);
            }

            stream = new SalmonMediaDataSource(file, MEDIA_BUFFER_SIZE, MEDIA_THREADS);
            mediaPlayer.Open(new MediaInputStream(stream));
            StartTimer();
        }

        class MediaInputStream : IMediaInputStream
        {
            private readonly object ReadLock = new object();

            byte[] buff;

            public Uri StreamUri => new Uri("dummy://data.dat");

            public bool CanSeek => true;

            public int ReadBufferLength => MEDIA_BUFFER_SIZE;

            public InputStreamInitializing OnInitializing { get; }

            public InputStreamInitialized OnInitialized { get; }

            private Stream stream;

            public MediaInputStream(Stream stream)
            {
                this.stream = stream;
                buff = new byte[ReadBufferLength];
            }

            unsafe int IMediaInputStream.Read(void* opaque, byte* targetBuffer, int targetBufferLength)
            {
                lock (ReadLock)
                {
                    int bytesRead = stream.Read(buff, 0, Math.Min(buff.Length, targetBufferLength));
                    if (bytesRead > 0)
                        Marshal.Copy(buff, 0, (IntPtr)targetBuffer, bytesRead);
                    return bytesRead;
                }
            }

            unsafe long IMediaInputStream.Seek(void* opaque, long offset, int whence)
            {
                lock (ReadLock)
                {
                    return whence == ffmpeg.AVSEEK_SIZE ?
                           stream.Length : stream.Seek(offset, SeekOrigin.Begin);
                }
            }

            void IDisposable.Dispose()
            {
                stream.Close();
            }
        }

        private void StopTimer()
        {
            quit = true;
        }

        private void StartTimer()
        {
            timer = new Thread(() =>
            {
                while (!quit)
                {
                    WindowUtils.RunOnMainThread(() =>
                    {
                        if (mediaPlayer != null)
                        {
                            int progressInt = (int)(mediaPlayer.Position.Ticks / (double) mediaPlayer.NaturalDuration.Value.Ticks * 1000);
                            SliderValue = progressInt;
                            DateTime curr = DateTimeOffset.FromUnixTimeMilliseconds((int) mediaPlayer.Position.TotalMilliseconds).UtcDateTime;
                            DateTime total = DateTimeOffset.FromUnixTimeMilliseconds((int) mediaPlayer.NaturalDuration.Value.TotalMilliseconds).UtcDateTime;
                            CurrTime = curr.ToString(dtFormat);
                            TotalTime = total.ToString(dtFormat);
                        }
                    });
                    Thread.Sleep(1000);
                }
            });
            timer.Start();
        }

        public void TogglePlay()
        {
            if (mediaPlayer.IsPlaying)
            {
                Pause();
            }
            else
                Play();
        }

        public void OnSliderChanged(int value)
        {
            int posMillis = (int)(mediaPlayer.NaturalDuration.Value.TotalMilliseconds * value / 1000);
            TimeSpan duration = TimeSpan.FromMilliseconds(posMillis);
            mediaPlayer.Seek(duration);
        }

        internal void SetMediaPlayer(Unosquare.FFME.MediaElement mediaPlayer)
        {
            Unosquare.FFME.Library.FFmpegDirectory = FFMPEG_DIR;
            this.mediaPlayer = mediaPlayer;
        }
    }

}