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
using System.Collections.Concurrent;
using IImage = Microsoft.Maui.Graphics.IImage;
using System.Text;
using System.Security.Cryptography;
using Salmon.Vault.ViewModel;
using System.Runtime.CompilerServices;
using Salmon.Vault.Utils;
using Salmon.Vault.Model;
using System;
using Microsoft.Maui.Controls;
using System.IO;
using Microsoft.Maui.Graphics;
using System.Collections.Generic;

#if IOS || ANDROID || MACCATALYST
using Microsoft.Maui.Graphics.Platform;
#elif WINDOWS
using Microsoft.Maui.Graphics.Win2D;
#endif

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

    private static readonly int MAX_CACHE_SIZE = 10 * 1024 * 1024;

    private static readonly ConcurrentDictionary<SalmonFileViewModel, byte[]> cache = new ConcurrentDictionary<SalmonFileViewModel, byte[]>();
    private static int cacheSize;

    /// <summary>
    /// Returns a bitmap thumbnail from an encrypted file
    /// </summary>
    /// <param name="salmonFile">The encrypted media file which will be used to get the thumbnail</param>
    /// <returns></returns>
    public static ImageSource GetVideoThumbnail(SalmonFile salmonFile)
    {
        return GetVideoThumbnail(salmonFile, 0);
    }

    public static ImageSource GetVideoThumbnail(SalmonFile salmonFile, long ms)
    {
        throw new NotSupportedException();
    }

    public static ImageSource GetVideoThumbnailMedia(IRealFile file, long ms)
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
        MemoryStream ms = new MemoryStream((int)maxSize);
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
    public static ImageSource GenerateThumbnail(SalmonFileViewModel item)
    {
        if (cache.ContainsKey(item) && cache[item] != null)
        {
            return ImageSource.FromStream(() =>
            {
                return new MemoryStream(cache[item]);
            });
        }
        // we might have multiple requests so we make sure we process only once
        ImageSource bitmapImage = null; //  GetIcon(item.GetSalmonFile());
        cache[item] = null;
        SalmonFileAttrQueue.UpdatePropertyAsync(() =>
        {
            ImageSource bitmapImage = null;
            if (item.GetSalmonFile().IsDirectory || !SalmonFileUtils.IsImage(item.GetSalmonFile().BaseName))
            {
                bitmapImage = GetIcon(item.GetSalmonFile());
            }
            else
            {
                MemoryStream nStream = GenerateThumbnail(item.GetSalmonFile());
                byte[] image = nStream.ToArray();
                bitmapImage = ImageSource.FromStream(() =>
                {
                    return new MemoryStream(image);
                });
                AddCache(item, image);
            }
            return bitmapImage;
        }, (bitmapImage) => UpdateImage(item, bitmapImage));
        return bitmapImage;
    }

    private static void UpdateImage(SalmonFileViewModel item, ImageSource imageSource)
    {
        WindowUtils.RunOnMainThread(() =>
        {
            try
            {
                item.Image = imageSource;
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine(ex);
            }
        });
    }

    public static ImageSource GetIcon(SalmonFile salmonFile)
    {
        string icon = salmonFile.IsFile ? "file_small.png" : "folder_small.png";
        // make sure that the image files are marked as MauiImage in VS properties
        ImageSource image = ImageSource.FromFile(icon);
        return image;
    }

    private static MemoryStream GenerateThumbnail(SalmonFile file)
    {
        Stream stream = FromFile(file);
        IImage image = null;
#if IOS || ANDROID || MACCATALYST
        // PlatformImage isn't currently supported on Windows.
        image = PlatformImage.FromStream(stream);
#elif WINDOWS
        // see: https://github.com/dotnet/Microsoft.Maui.Graphics/issues/422
        // use W2DImage as an alternative when downsize becomes available:
        // https://github.com/dotnet/maui/issues/16767
        // image = new W2DImageLoadingService().FromStream(stream);
#endif
        if (image != null)
        {
            stream.Close();
            image = ResizeImage(image, THUMBNAIL_SIZE);
            return FromImage(image);
        }
        else
        {
            MemoryStream ms = new MemoryStream();
            stream.CopyTo(ms, SalmonDefaultOptions.BufferSize);
            ms.Flush();
            stream.Close();
            ms.Position = 0;
            return ms;
        }
    }

    private static MemoryStream FromImage(IImage image)
    {
        MemoryStream ms = new MemoryStream();
        image.Save(ms, ImageFormat.Bmp);
        ms.Position = 0;
        return ms;
    }

    private static IImage ResizeImage(IImage image, int size)
    {
        float ratio = image.Width / (float)image.Height;
        int nWidth = ratio > 1 ? size : (int)(size * ratio);
        int nHeight = ratio < 1 ? size : (int)(size / ratio);
        return image.Downsize(nWidth, nHeight);
    }

    [MethodImpl(MethodImplOptions.Synchronized)]
    private static void AddCache(SalmonFileViewModel item, byte[] image)
    {
        if (cacheSize > MAX_CACHE_SIZE)
            ResetCache();
        cache[item] = image;
        try
        {
            if (image != null)
            {
                cacheSize += image.Length;
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine(ex);
        }
    }

    private static void ResetCache()
    {
        int reduceSize = 0;
        List<SalmonFileViewModel> keysToRemove = new List<SalmonFileViewModel>();
        foreach (SalmonFileViewModel key in cache.Keys)
        {
            byte[] bitmap = cache[key];
            if (bitmap != null)
            {
                reduceSize += bitmap.Length;
            }
            if (reduceSize >= MAX_CACHE_SIZE / 2)
                break;
            keysToRemove.Add(key);
        }
        foreach (SalmonFileViewModel key in keysToRemove)
        {
            cache.Remove(key, out byte[] bitmap);
            if (bitmap != null)
            {
                cacheSize -= bitmap.Length;
            }
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

    public static Color GetTintColor(SalmonFile item)
    {
        if (!item.IsFile || SalmonFileUtils.IsImage(item.BaseName))
            return Colors.Transparent;
        MD5 md5 = MD5.Create();
        string ext = GetExt(item);
        byte[] hashValue = md5.ComputeHash(Encoding.UTF8.GetBytes(ext));
        string hashstring = BitConverter.ToHex(hashValue).Substring(0, 6);
        return Color.FromArgb("#88" + hashstring);
    }

    public static string GetExt(SalmonFile salmonFile)
    {
        if (!salmonFile.IsFile || SalmonFileUtils.IsImage(salmonFile.BaseName))
            return "";
        return SalmonFileUtils.GetExtensionFromFileName(salmonFile.BaseName).ToLower();
    }
}