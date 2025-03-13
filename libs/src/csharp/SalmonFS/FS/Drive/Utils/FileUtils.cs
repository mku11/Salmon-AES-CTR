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

namespace Mku.FS.Drive.Utils;

/// <summary>
/// File utilities
/// </summary>
public class FileUtils
{
    /// <summary>
    ///  Detect if filename is a text file.
	/// </summary>
	///  <param name="filename">The filename.</param>
    ///  <returns>True if text file.</returns>
    public static bool IsText(string filename)
    {
        string ext = GetExtensionFromFileName(filename).ToLower();
        return ext.Equals("txt");
    }

    /// <summary>
    ///  Detect if filename is an image file.
	/// </summary>
	///  <param name="filename">The filename.</param>
    ///  <returns>True if image file.</returns>
    public static bool IsImage(string filename)
    {
        string ext = GetExtensionFromFileName(filename).ToLower();
        return ext.Equals("png") || ext.Equals("jpg") || ext.Equals("jpeg") || ext.Equals("bmp") 
            || ext.Equals("webp") || ext.Equals("gif") || ext.Equals("tif") || ext.Equals("tiff");
    }

    /// <summary>
    ///  Detect if filename is an audio file.
	/// </summary>
	///  <param name="filename">The filename.</param>
    ///  <returns>True if audio file.</returns>
    public static bool IsAudio(string filename)
    {
        string ext = GetExtensionFromFileName(filename).ToLower();
        return ext.Equals("wav") || ext.Equals("mp3");
    }

    /// <summary>
    ///  Detect if filename is a video file.
	/// </summary>
	///  <param name="filename">The filename.</param>
    ///  <returns>True if video file.</returns>
    public static bool IsVideo(string filename)
    {
        string ext = GetExtensionFromFileName(filename).ToLower();
        return ext.Equals("mp4");
    }
	
	/// <summary>
    ///  Detect if filename is a pdf file.
	/// </summary>
	///  <param name="filename">The filename.</param>
    ///  <returns>True if pdf file.</returns>
    public static bool IsPdf(string filename)
    {
        string ext = GetExtensionFromFileName(filename).ToLower();
        return ext.Equals("pdf");
    }


    /// <summary>
    ///  Return the extension of a filename.
	/// </summary>
	///  <param name="fileName">The file name</param>
    public static string GetExtensionFromFileName(string fileName)
    {
        if (fileName == null)
            return "";
        int index = fileName.LastIndexOf(".");
        if (index >= 0)
        {
            return fileName.Substring(index + 1);
        }
        else
            return "";
    }

    /// <summary>
    ///  Return a filename without extension
	/// </summary>
	///  <param name="fileName">The file name</param>
    public static string GetFileNameWithoutExtension(string fileName)
    {
        if (fileName == null)
            return "";
        int index = fileName.LastIndexOf(".");
        if (index >= 0)
        {
            return fileName.Substring(0, index);
        }
        else
            return "";
    }
}
