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
using Salmon.Vault.Media;
using Salmon.Vault.ViewModel;
using System;
using System.ComponentModel;
using System.Windows.Controls;
using System.Windows.Input;

namespace Salmon.Vault.View
{
    public partial class MediaPlayer : System.Windows.Window
    {
        private bool dragEntered;

        MediaPlayerViewModel ViewModel { get; set; }
        public MediaPlayer(SalmonFileViewModel viewModel)
        {
            InitializeComponent();
            ViewModel = new MediaPlayerViewModel();
            DataContext = ViewModel;
            PlayerSlider.AddHandler(MouseLeftButtonUpEvent, new MouseButtonEventHandler(Slider_MouseLeftButtonUp), true);
            PlayerSlider.AddHandler(MouseLeftButtonDownEvent, new MouseButtonEventHandler(Slider_MouseLeftButtonDown), true);
            PlayerSlider.AddHandler(MouseMoveEvent, new MouseEventHandler(Slider_MouseMove), true);
            Closing += (object sender, CancelEventArgs e) => ViewModel.OnClose();
            ViewModel.OpenMedia = (stream, bufferSize) => Media.Open(new FFMPEGMediaInput(stream, bufferSize));
            ViewModel.PlayMedia = () => Media.Play();
            ViewModel.StopMedia = () => Media.Stop();
            ViewModel.PauseMedia = () => Media.Pause();
            ViewModel.CloseMedia = () => Media.Close();
            ViewModel.SeekMedia = (ms) => Media.Seek(TimeSpan.FromMilliseconds(ms));
            ViewModel.GetMediaDuration = () => (long)Media.NaturalDuration.Value.TotalMilliseconds;
            ViewModel.IsMediaPlaying = () => Media.IsPlaying;
            Unosquare.FFME.Library.FFmpegDirectory = FFMPEGMediaInput.FFMPEG_DIR;
            ViewModel.Load(viewModel);
        }

        private void Slider_MouseLeftButtonDown(object sender, MouseButtonEventArgs e)
        {
            dragEntered = true;
            ViewModel.OnSliderChanged((int)((Slider)sender).Value, true);
        }

        private void Slider_MouseMove(object sender, MouseEventArgs e)
        {
            if(dragEntered)
                ViewModel.OnSliderChanged((int)((Slider)sender).Value, true);
        }

        private void Slider_MouseLeftButtonUp(object sender, MouseEventArgs e)
        {
            ViewModel.OnSliderChanged((int)((Slider)sender).Value, false);
            dragEntered = false;
        }


    }
}