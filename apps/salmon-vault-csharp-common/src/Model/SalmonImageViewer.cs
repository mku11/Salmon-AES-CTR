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
using System;
using System.ComponentModel;
using System.IO;

namespace Salmon.Vault.Model;

public class SalmonImageViewer : INotifyPropertyChanged
{
	private static int MEDIA_BUFFERS = 4;
	private static int MEDIA_BUFFER_SIZE = 4 * 1024 * 1024;
	private static int MEDIA_THREADS = 1;
	private static int MEDIA_BACKOFFSET = 256 * 1024;
	
	
    public event PropertyChangedEventHandler PropertyChanged;
    public Stream ImageStream { get; private set; }

    public void Load(SalmonFile salmonFile)
    {
        try
        {
            ImageStream = new SalmonFileInputStream(salmonFile, 
				MEDIA_BUFFERS, MEDIA_BUFFER_SIZE,
                MEDIA_THREADS, MEDIA_BACKOFFSET);
        }
        catch (Exception e)
        {
            Console.Error.WriteLine(e);
        }
    }

    public void OnClosing()
    {
        if (ImageStream != null)
        {
            ImageStream.Close();
        }
    }
}