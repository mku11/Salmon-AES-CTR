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

using HeyRed.Mime;
using Mku.SalmonFS;
using Mku.Utils;
using Salmon.Vault.Services;
using System;
using System.ComponentModel;
using System.IO;

namespace Salmon.Vault.Model;

public class SalmonContentViewer : INotifyPropertyChanged
{
    private static readonly string URL = "https://localhost/";
    private static readonly int BUFFERS = 4;
    private static readonly int BUFFER_SIZE = 4 * 1024 * 1024;
    private static readonly int THREADS = 4;
    private static readonly int BACK_OFFSET = 256 * 1024;
    private readonly IWebBrowserService webBrowserService;
    private Stream stream;

    public string _source;
    public string Source
    {
        get => _source;
        set
        {
            if (_source != value)
            {
                _source = value;
                if (PropertyChanged != null)
                    PropertyChanged(this, new PropertyChangedEventArgs("Source"));
            }
        }
    }

    public event PropertyChangedEventHandler PropertyChanged;

    public SalmonContentViewer()
    {
        webBrowserService = ServiceLocator.GetInstance().Resolve<IWebBrowserService>();
    }

    public void OnClose()
    {
        if (stream != null)
            stream.Close();
    }

    internal void Load(SalmonFile file)
    {
        string filePath = null;
        try
        {
            filePath = file.RealPath;
        }
        catch (Exception e)
        {
            Console.Error.WriteLine(e);
        }
        string filename = file.BaseName;
        string mimeType = MimeTypesMap.GetMimeType(filename);
        // webview2 buffering with partial content works only with video and audio
        bool buffered = SalmonFileUtils.IsVideo(filename) || SalmonFileUtils.IsAudio(filename);
        string contentPath = "content.dat";
        webBrowserService.SetResponse(URL + contentPath, mimeType, file.Size, BUFFER_SIZE, buffered, (pos) =>
        {
            if (stream != null)
                stream.Close();
            SalmonFileInputStream fileStream = new SalmonFileInputStream(file, BUFFERS, BUFFER_SIZE, THREADS, BACK_OFFSET);
            // we need to offset the start of the stream so the webview can see it as partial content
            if (buffered)
            {
                fileStream.PositionStart = pos;
                fileStream.PositionEnd = pos + BUFFER_SIZE - 1;
                fileStream.Position = 0;
            }
            stream = fileStream;
            return stream;
        });
        Source = URL + contentPath;
    }
}