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
using Android.Graphics;
using Android.Media;
using Android.OS;
using Android.Provider;
using Java.IO;
using Mku.Salmon.IO;
using Mku.SalmonFS;
using Mku.Time;
using Mku.Utils;
using Salmon.Vault.Main;


namespace Salmon.Vault.Image;

/// <summary>
/// Utility class that generates thumbnails for encrypted salmon files
/// </summary>
public class Thumbnails
{
    private static readonly string TMP_THUMB_DIR = "tmp";
    private static readonly int TMP_VIDEO_THUMB_MAX_SIZE = 5 * 1024 * 1024;
    private static readonly int TMP_GIF_THUMB_MAX_SIZE = 512 * 1024;
    private static readonly int BUFFER_SIZE = 256 * 1024;

    /**
     * Returns a bitmap thumbnail from an encrypted file
     *
     * @param salmonFile The encrypted media file which will be used to get the thumbnail
     */
    public static Bitmap GetVideoThumbnail(SalmonFile salmonFile)
    {
        return GetVideoThumbnail(salmonFile, 0);
    }

    public static Bitmap GetVideoThumbnail(SalmonFile salmonFile, long ms)
    {
        Bitmap bitmap = null;
        Java.IO.File tmpFile = null;
        try
        {
            tmpFile = GetVideoTmpFile(salmonFile);
            if (ms > 0)
                bitmap = GetVideoThumbnailMedia(tmpFile, ms);
            else
                bitmap = ThumbnailUtils.CreateVideoThumbnail(tmpFile.Path, ThumbnailKind.FullScreenKind);
        }
        catch (System.Exception ex)
        {
            System.Console.Error.WriteLine(ex);
        }
        finally
        {
            if (tmpFile != null)
            {
                tmpFile.Delete();
                tmpFile.DeleteOnExit();
            }
        }
        return bitmap;
    }

    public static Bitmap GetVideoThumbnailMedia(Java.IO.File file, long ms)
    {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        Bitmap bitmap = null;
        try
        {
            retriever.SetDataSource(file.Path);
            bitmap = retriever.GetFrameAtTime(ms * 1000);
        }
        catch (System.Exception ex)
        {
            System.Console.Error.WriteLine(ex);
        }
        finally
        {
            try
            {
                retriever.Release();
				if (Build.VERSION.SdkInt >= BuildVersionCodes.Q) {
                    retriever.Close();
                }
            }
            catch (System.IO.IOException e)
            {
                System.Console.Error.WriteLine(e);
            }
        }
        return bitmap;
    }

    /**
     * Create a partial temp file from an encrypted file that will be used to retrieve the thumbnail
     *
     * @param salmonFile The encrypted file that will be used to get the temp file
     */
    private static Java.IO.File GetVideoTmpFile(SalmonFile salmonFile)
    {
        Java.IO.File tmpDir = new Java.IO.File(SalmonApplication.GetInstance().ApplicationContext.CacheDir, TMP_THUMB_DIR);
        if (!tmpDir.Exists())
            tmpDir.Mkdir();

        Java.IO.File tmpFile = new Java.IO.File(tmpDir, Time.CurrentTimeMillis() + "." + SalmonFileUtils.GetExtensionFromFileName(salmonFile.BaseName));
        if (tmpFile.Exists())
            tmpFile.Delete();
        tmpFile.CreateNewFile();
        FileOutputStream fileStream = new FileOutputStream(tmpFile);
        SalmonStream ins = salmonFile.GetInputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        long totalBytesRead = 0;
        while ((bytesRead = ins.Read(buffer, 0, buffer.Length)) > 0
                && totalBytesRead < TMP_VIDEO_THUMB_MAX_SIZE)
        {
            fileStream.Write(buffer, 0, bytesRead);
            totalBytesRead += bytesRead;
        }
        fileStream.Flush();
        fileStream.Close();
        ins.Close();
        return tmpFile;
    }

    /**
     * Return a MemoryStream with the partial unencrypted file contents.
     * This will read only the beginning contents of the file since we don't need the whole file.
     *
     * @param salmonFile The encrypted file to be used
     * @param maxSize    The max content length that will be decrypted from the beginning of the file
     */
    private static System.IO.Stream GetTempStream(SalmonFile salmonFile, long maxSize)
    {
        System.IO.MemoryStream ms = new System.IO.MemoryStream();
        SalmonStream ins = salmonFile.GetInputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        long totalBytesRead = 0;
        while ((bytesRead = ins.Read(buffer, 0, buffer.Length)) > 0
                && totalBytesRead < maxSize)
        {
            ms.Write(buffer, 0, bytesRead);
            totalBytesRead += bytesRead;
        }
        ms.Flush();
        ins.Close();
        ms.Position = 0;
        return ms;
    }

    /**
     * Create a bitmap from the unecrypted data contents of a media file
     * If the file is a gif we get only a certain amount of data from the beginning of the file
     * since we don't need to get the whole file.
     *
     * @param salmonFile
     */
    public static Bitmap GetImageThumbnail(SalmonFile salmonFile)
    {
        System.IO.Stream stream = null;
        Bitmap bitmap = null;
        try
        {
            string ext = SalmonFileUtils.GetExtensionFromFileName(salmonFile.BaseName).ToLower();
            if (ext.Equals("gif") && salmonFile.Size > TMP_GIF_THUMB_MAX_SIZE)
                stream = new System.IO.BufferedStream(GetTempStream(salmonFile, TMP_GIF_THUMB_MAX_SIZE), BUFFER_SIZE);
            else
                stream = new System.IO.BufferedStream(salmonFile.GetInputStream(), BUFFER_SIZE);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.InSampleSize = 4;
            bitmap = BitmapFactory.DecodeStream(stream, null, options);
        }
        catch (System.Exception ex)
        {
            System.Console.Error.WriteLine(ex);
        }
        finally
        {
            if (stream != null)
                stream.Close();
        }
        return bitmap;
    }
}