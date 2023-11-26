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

using Mku.File;
using Mku.Salmon;
using Mku.Salmon.IO;
using Mku.SalmonFS;
using Mku.Utils;
using Salmon.Vault.ViewModel;
using Salmon.Vault.Utils;
using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.IO;
using System.Reflection;
using System.Security.Cryptography;
using System.Text;
using System.Threading.Tasks;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using BitConverter = Mku.Convert.BitConverter;

namespace Salmon.Vault.Image;

/// <summary>
/// Utility for thumbnail generation
/// </summary>
public class Thumbnails
{
    private static readonly int TMP_VIDEO_THUMB_MAX_SIZE = 5 * 1024 * 1024;
    private static readonly int TMP_GIF_THUMB_MAX_SIZE = 512 * 1024;
    private static readonly int BUFFER_SIZE = 256 * 1024;
    private static readonly int THUMBNAIL_SIZE = 128;
    private static readonly int MAX_CACHE_SIZE = 20 * 1024 * 1024;

    private static readonly ConcurrentDictionary<SalmonFile, BitmapImage> cache = new ConcurrentDictionary<SalmonFile, BitmapImage>();
    private static int cacheSize;

    /// <summary>
    /// Returns a bitmap thumbnail from an encrypted file
    /// </summary>
    /// <param name="salmonFile">The encrypted media file which will be used to get the thumbnail</param>
    /// <returns></returns>
    public static BitmapImage GetVideoThumbnail(SalmonFile salmonFile)
    {
        return GetVideoThumbnail(salmonFile, 0);
    }

    public static BitmapImage GetVideoThumbnail(SalmonFile salmonFile, long ms)
    {
        throw new NotSupportedException();
    }

    public static BitmapImage GetVideoThumbnailMedia(IRealFile file, long ms)
    {
        throw new NotSupportedException();
    }

    /// <summary>
    /// Create a partial temp file from an encrypted file that will be used to get the thumbnail
    /// </summary>
    /// <param name="salmonFile">The encrypted file that will be used to get the temp file</param>
    /// <returns></returns>
    private static IRealFile GetVideoTmpFile(SalmonFile salmonFile)
    {
        throw new NotSupportedException();
    }

    /// <summary>
    /// Return a MemoryStream with the partial unencrypted file contents.
    /// This will read only the beginning contents of the file since we don't need the whole file.
    /// </summary>
    /// <param name="salmonFile">The encrypted file to be used</param>
    /// <param name="maxSize">The max content length that will be decrypted from the beginning of the file</param>
    /// <returns></returns>
    private static Stream GetTempStream(SalmonFile salmonFile, long maxSize)
    {
        MemoryStream ms = new MemoryStream();
        SalmonDefaultOptions.BufferSize = 1 * 1024 * 1024;
        SalmonStream ins = salmonFile.GetInputStream();
        ins.CopyTo(ms, SalmonDefaultOptions.BufferSize);
        ms.Flush();
        ins.Close();
        ms.Position = 0;
        return ms;
    }

    /// <summary>
    /// Create a bitmap from the unencrypted data contents of a media file
    /// If the file is a gif we get only a certain amount of data from the beginning of the file
    /// since we don't need to get the whole file.
    /// </summary>
    /// <param name="salmonFile"></param>
    /// <returns></returns>
    public static void GenerateThumbnail(SalmonFileViewModel item)
    {

        BitmapImage bitmapImage;
        if (cache.ContainsKey(item.GetSalmonFile()))
        {
            bitmapImage = cache[item.GetSalmonFile()];
            WindowUtils.RunOnMainThread(() =>
            {
                item.Image = bitmapImage;
            });
        }
        else if (item.GetSalmonFile().IsDirectory || !SalmonFileUtils.IsImage(item.GetSalmonFile().BaseName))
        {
            WindowUtils.RunOnMainThread(() =>
            {
                bitmapImage = GetIcon(item.GetSalmonFile());
                AddCache(item.GetSalmonFile(), bitmapImage);
                item.Image = bitmapImage;
            });
        }
        else
        {
            // we might have multiple requests so we make sure we process only once
            AddCache(item.GetSalmonFile(), null);

            Task.Run(() =>
            {
                Stream nStream = GenerateThumbnail(item.GetSalmonFile());
                WindowUtils.RunOnMainThread(() =>
                {
                    try
                    {
                        bitmapImage = new BitmapImage();
                        bitmapImage.BeginInit();
                        bitmapImage.CacheOption = BitmapCacheOption.OnLoad;
                        bitmapImage.StreamSource = nStream;
                        bitmapImage.EndInit();
                        bitmapImage.Freeze();
                        item.Image = bitmapImage;
                        AddCache(item.GetSalmonFile(), bitmapImage);
                    }
                    catch (Exception ex)
                    {
                        Console.Error.WriteLine(ex);
                    }
                });
            });
        }
    }

    private static BitmapImage GetIcon(SalmonFile salmonFile)
    {
        string icon = salmonFile.IsFile ? "Icons/file_small.png" : "Icons/folder_small.png";
        Uri uri = new Uri("pack://application:,,,/"
            + Assembly.GetAssembly(typeof(Thumbnails)).GetName().Name
            + ";component/" + icon, UriKind.RelativeOrAbsolute);
        BitmapImage bitmapImage = new BitmapImage(uri);
        return bitmapImage;
    }

    private static Stream GenerateThumbnail(SalmonFile file)
    {
        Stream stream = FromFile(file);
        System.Drawing.Bitmap bitmap = new System.Drawing.Bitmap(stream);
        stream.Close();
        bitmap = ResizeBitmap(bitmap, THUMBNAIL_SIZE);
        return FromBitmap(bitmap);
    }

    private static Stream FromBitmap(System.Drawing.Bitmap bitmap)
    {
        MemoryStream ms = new MemoryStream();
        bitmap.Save(ms, System.Drawing.Imaging.ImageFormat.Bmp);
        ms.Position = 0;
        return ms;
    }

    private static System.Drawing.Bitmap ResizeBitmap(System.Drawing.Bitmap bitmap, int size)
    {
        float ratio = bitmap.Width / (float)bitmap.Height;
        int nWidth = ratio > 1 ? size : (int)(size * ratio);
        int nHeight = ratio < 1 ? size : (int)(size / ratio);
        return new System.Drawing.Bitmap(bitmap, new System.Drawing.Size(nWidth, nHeight));
    }

    private static void AddCache(SalmonFile file, BitmapImage image)
    {
        if (cacheSize > MAX_CACHE_SIZE)
            ResetCache();
        cache[file] = image;
        try
        {
            if(image != null)
                cacheSize += (int)(image.Width * image.Height * 4);
        }
        catch (Exception ex)
        {
            Console.WriteLine(ex);
        }
    }

    private static void ResetCache()
    {
        int reduceSize = 0;
        List<SalmonFile> keysToRemove = new List<SalmonFile>();
        foreach (SalmonFile key in cache.Keys)
        {
            BitmapImage bitmap = cache[key];
            if (bitmap != null)
                reduceSize += (int)(bitmap.Width * bitmap.Height * 4);
            if (reduceSize >= MAX_CACHE_SIZE / 2)
                break;
            keysToRemove.Add(key);
        }
        foreach (SalmonFile key in keysToRemove)
        {
            cache.Remove(key, out BitmapImage bitmap);
            if (bitmap != null)
                cacheSize -= (int)(bitmap.Width * bitmap.Height * 4);
        }
    }

    private static Stream FromFile(SalmonFile file)
    {
        Stream stream = null;
        try
        {
            string ext = SalmonFileUtils.GetExtensionFromFileName(file.BaseName).ToLower();
            if (ext.Equals("gif") && file.Size > TMP_GIF_THUMB_MAX_SIZE)
                stream = GetTempStream(file, TMP_GIF_THUMB_MAX_SIZE);
            else
                stream = new BufferedStream(file.GetInputStream(), BUFFER_SIZE);
            stream.Position = 0;
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
        }
        return stream;
    }

    public static Color GetTintColor(SalmonFile salmonFile)
    {
        if (!salmonFile.IsFile || SalmonFileUtils.IsImage(salmonFile.BaseName))
            return Colors.Transparent;

        MD5 md5 = MD5.Create();
        string ext = GetExt(salmonFile);
        byte[] hashValue = md5.ComputeHash(Encoding.UTF8.GetBytes(ext));
        string hashstring = BitConverter.ToHex(hashValue);
        Color color = (Color)ColorConverter.ConvertFromString("#" + hashstring.Substring(0, 6));
        return color;
    }

    public static string GetExt(SalmonFile salmonFile)
    {
        if (!salmonFile.IsFile || SalmonFileUtils.IsImage(salmonFile.BaseName))
            return "";
        return SalmonFileUtils.GetExtensionFromFileName(salmonFile.BaseName).ToLower();
    }
}