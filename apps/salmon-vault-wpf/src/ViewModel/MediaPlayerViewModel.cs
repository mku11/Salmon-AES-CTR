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
using Mku.SalmonFS;
using Salmon.Vault.Config;
using Salmon.Vault.Dialog;
using Salmon.Vault.Media;
using Salmon.Vault.Utils;

using System;
using System.ComponentModel;
using System.IO;
using System.Threading;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Imaging;

namespace Salmon.Vault.ViewModel;

public class MediaPlayerViewModel : INotifyPropertyChanged
{
    private static readonly int MEDIA_BUFFERS = 4;
    private static readonly int MEDIA_BUFFER_SIZE = 4 * 1024 * 1024;
    private static readonly int MEDIA_THREADS = 4;
    private static readonly int MEDIA_BACK_OFFSET = 256 * 1024;

    private SalmonFileViewModel item;

    public delegate void OpenMediaPlayer(Stream stream, int bufferSize);
    public OpenMediaPlayer OpenMedia;

    public delegate void CloseMediaPlayer();
    public CloseMediaPlayer CloseMedia;

    public delegate void PlayMediaPlayer();
    public PlayMediaPlayer PlayMedia;

    public delegate void SeekMediaPlayer(long pos);
    public SeekMediaPlayer SeekMedia;

    public delegate void PauseMediaPlayer();
    public PauseMediaPlayer PauseMedia;

    public delegate void StopMediaPlayer();
    public StopMediaPlayer StopMedia;

    public delegate bool IsMediaPlayerPlaying();
    public IsMediaPlayerPlaying IsMediaPlaying;

    public delegate long GetMediaPlayerDuration();
    public GetMediaPlayerDuration GetMediaDuration;

    public ImageSource _playImageSource;
    public ImageSource PlayImageSource
    {
        get => _playImageSource;
        set
        {
            if (_playImageSource != value)
            {
                _playImageSource = value;
                if (PropertyChanged != null)
                    PropertyChanged(this, new PropertyChangedEventArgs("PlayImageSource"));
            }
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

    public long _currTime;
    public long CurrTime
    {
        get => _currTime;
        set
        {
            if (_currTime != value)
            {
                _currTime = value;
                if (PropertyChanged != null)
                    PropertyChanged(this, new PropertyChangedEventArgs("CurrTime"));
            }
        }
    }

    public long _totalTime;
    public long TotalTime
    {
        get => _totalTime;
        set
        {
            if (_totalTime != value)
            {
                _totalTime = value;
                if (PropertyChanged != null)
                    PropertyChanged(this, new PropertyChangedEventArgs("TotalTime"));
            }
        }
    }

    public long _mediaPosition;
    public long MediaPosition
    {
        get => _mediaPosition;
        set
        {
            if (_mediaPosition != value)
            {
                _mediaPosition = value;
                if (PropertyChanged != null)
                    PropertyChanged(this, new PropertyChangedEventArgs("MediaPosition"));
            }
        }
    }

    private Timer timer;

    ImageSource playImage = GetImageSource("/icons/play.png");
    ImageSource pauseImage = GetImageSource("/icons/pause.png");

    public event PropertyChangedEventHandler PropertyChanged;

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
    private Stream stream;

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

    public bool IsMediaSliderDrag { get; set; }
    public int SliderPosition { get; private set; }

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

    public void OnClose()
    {
        Stop();
        CloseMedia();
        if (stream != null)
            stream.Close();
        StopTimer();
    }

    private void Play()
    {
        PlayMedia();
        PlayImageSource = pauseImage;
    }

    private void Pause()
    {
        PauseMedia();
        PlayImageSource = playImage;
    }

    private void Stop()
    {
        StopMedia();
        PlayImageSource = playImage;
    }

    public void Load(SalmonFileViewModel viewModel)
    {
        item = viewModel;
        SalmonFile file = ((SalmonFileViewModel)item).GetSalmonFile();
        string filePath = null;
        try
        {
            filePath = file.RealPath;
        }
        catch (Exception e)
        {
            Console.Error.WriteLine(e);
        }

        stream = new SalmonFileInputStream(file, MEDIA_BUFFERS, MEDIA_BUFFER_SIZE, MEDIA_THREADS, MEDIA_BACK_OFFSET);
        OpenMedia(stream, MEDIA_BUFFER_SIZE);
        StartTimer();
        Play();
    }


    private void StopTimer()
    {
        timer.Dispose();
    }

    private void StartTimer()
    {
        timer = new Timer((state) =>
        {
            long position = MediaPosition;
            long duration = GetMediaDuration();

            WindowUtils.RunOnMainThread(() =>
            {
                if (!IsMediaSliderDrag)
                {
                    SliderValue = (int)(position / (float)duration * 1000);
                    UpdateTime(position, duration);
                }
            });
        }, null, 0, 1000);
    }

    private void UpdateTime(long position, long duration)
    {
        CurrTime = position;
        TotalTime = duration;
    }

    public void TogglePlay()
    {
        if (IsMediaPlaying())
        {
            Pause();
        }
        else
            Play();
    }

    public void OnSliderChanged(int value, bool drag)
    {
        if (drag)
            IsMediaSliderDrag = true;
        int posMillis = (int)(GetMediaDuration() * value / 1000);
        UpdateTime(posMillis, GetMediaDuration());
        SeekMedia(posMillis);
        if (!drag)
            IsMediaSliderDrag = false;

    }

    internal static bool HasFFMPEG()
    {
        if (!Directory.Exists(FFMPEGMediaInput.FFMPEG_DIR)
            || !File.Exists(FFMPEGMediaInput.FFMPEG_DIR + "/ffmpeg.exe"))
        {
            SalmonDialog.PromptDialog("Media Player",
                "Could not find FFMPEG in " + FFMPEGMediaInput.FFMPEG_DIR
                + ". Download version " + SalmonConfig.FFMPEGLibraryVersion
                + " and copy the extract the contents of directory bin to directory "
                + FFMPEGMediaInput.FFMPEG_DIR,
                "Download", () => URLUtils.GoToUrl(SalmonConfig.FFMPEGLibraryURL),
                "Cancel");
            return false;
        }
        return true;
    }
}