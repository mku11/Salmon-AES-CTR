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
using LibVLCSharp.Shared;
using LibVLCSharp.WPF;
using Salmon.FS;
using Salmon.Model;
using Salmon.Window;
using SalmonFS.Media;
using System;
using System.ComponentModel;
using System.IO;
using System.Threading;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using MediaPlayer = LibVLCSharp.Shared.MediaPlayer;
using MediaType = Salmon.ViewModel.MainViewModel.MediaType;

namespace Salmon.ViewModel
{

    public class MediaPlayerViewModel : INotifyPropertyChanged
    {
        private static readonly int MEDIA_BUFFER_SIZE = 0;
        private static readonly int MEDIA_THREADS = 4;

        public Button playButton;

        private System.Windows.Window window;
        private FileItem item;

        private MediaType type;

        public MediaPlayer _mediaPlayer;
        public MediaPlayer MediaPlayer
        {
            get => _mediaPlayer;
            set
            {
                _mediaPlayer = value;
                if (PropertyChanged != null)
                    PropertyChanged(this, new PropertyChangedEventArgs("MediaPlayer"));
            }
        }


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
                    MediaPlayer.Dispose();
                    MediaPlayer = null;
                    if (stream != null)
                        stream.Close();

                });
            };
        }

        public static void OpenMediaPlayer(FileItem file, System.Windows.Window owner, MediaType type)
        {
            View.MediaPlayer mediaPlayer = new View.MediaPlayer();
            mediaPlayer.SetWindow(owner);
            mediaPlayer.Load(file, type);
            mediaPlayer.ShowDialog();
        }

        private void Play()
        {
            MediaPlayer.Play();
        }

        private void Stop()
        {
            MediaPlayer.Stop();
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

            LibVLC libvlc = new LibVLC(enableDebugLogs: true);
            stream = new SalmonMediaDataSource(file, MEDIA_BUFFER_SIZE, MEDIA_THREADS);
            MediaInput input = new StreamMediaInput(stream);
            Media media = new Media(libvlc, input);
            MediaPlayer mediaPlayer = new MediaPlayer(media);
            MediaPlayer = mediaPlayer;
            MediaPlayer.Paused += (object sender, EventArgs e) =>
            {
                PlayImageSource = playImage;
            };
            MediaPlayer.Playing += (object sender, EventArgs e) =>
            {
                PlayImageSource = pauseImage;
            };
            StartTimer();
            Play();
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
                        if (MediaPlayer != null)
                        {
                            int progressInt = (int)(MediaPlayer.Time / (double)MediaPlayer.Media.Duration * 1000);
                            SliderValue = progressInt;
                            DateTime curr = DateTimeOffset.FromUnixTimeMilliseconds(MediaPlayer.Time).UtcDateTime;
                            DateTime total = DateTimeOffset.FromUnixTimeMilliseconds(MediaPlayer.Media.Duration).UtcDateTime;
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
            if (MediaPlayer.IsPlaying)
            {
                MediaPlayer.Pause();
            }
            else
                MediaPlayer.Play();
        }

        public void OnSliderChanged(int value)
        {
            int posMillis = (int)(MediaPlayer.Media.Duration * (double)value / 1000);
            TimeSpan duration = TimeSpan.FromMilliseconds(posMillis);
            MediaPlayer.SeekTo(duration);
        }
    }

}