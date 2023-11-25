package com.mku.salmon.vault.image;
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

import com.mku.convert.BitConverter;
import com.mku.io.RandomAccessStream;
import com.mku.io.InputStreamWrapper;
import com.mku.io.MemoryStream;
import com.mku.salmon.io.SalmonStream;
import com.mku.salmonfs.SalmonFile;
import com.mku.utils.SalmonFileUtils;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.*;

/**
 * Utility class that generates thumbnails for encrypted salmon files
 */
public class Thumbnails {
    private static final String TMP_THUMB_DIR = "tmp";
    private static final int TMP_VIDEO_THUMB_MAX_SIZE = 3 * 1024 * 1024;
    private static final int TMP_GIF_THUMB_MAX_SIZE = 512 * 1024;
    private static final int ENC_BUFFER_SIZE = 128 * 1024;
    private static final int THUMBNAIL_SIZE = 128;

    private static final int MAX_CACHE_SIZE = 20 * 1024 * 1024;
    private static final HashMap<SalmonFile, Image> cache = new HashMap<>();
    private static final int TINT_COLOR_ALPHA = 127;
    private static int cacheSize;

    private static final Executor executor = Executors.newFixedThreadPool(2);
    private static final LinkedBlockingDeque<ThumbnailTask> tasks = new LinkedBlockingDeque<>();

    private static class ThumbnailTask {
        SalmonFile file;
        ImageView view;

        public ThumbnailTask(SalmonFile salmonFile, ImageView imageView) {
            this.file = salmonFile;
            this.view = imageView;
        }
    }

    /// <summary>
    /// Returns a bitmap thumbnail from an encrypted file
    /// </summary>
    /// <param name="salmonFile">The encrypted media file which will be used to get the thumbnail</param>
    /// <returns></returns>
    public static ImageView getVideoThumbnail(SalmonFile salmonFile) {
        return getVideoThumbnail(salmonFile, 0);
    }

    public static ImageView getVideoThumbnail(SalmonFile salmonFile, long ms) {
        throw new UnsupportedOperationException();
    }

    public static ImageView getVideoThumbnailMedia(File file, long ms) {
        throw new UnsupportedOperationException();
    }

    /// <summary>
    /// Create a partial temp file from an encrypted file that will be used to get the thumbnail
    /// </summary>
    /// <param name="salmonFile">The encrypted file that will be used to get the temp file</param>
    /// <returns></returns>
    private static File getVideoTmpFile(SalmonFile salmonFile) {
        throw new UnsupportedOperationException();
    }

    /// <summary>
    /// Return a MemoryStream with the partial unencrypted file contents.
    /// This will read only the beginning contents of the file since we don't need the whole file.
    /// </summary>
    /// <param name="salmonFile">The encrypted file to be used</param>
    /// <param name="maxSize">The max content length that will be decrypted from the beginning of the file</param>
    /// <returns></returns>
    private static RandomAccessStream getTempStream(SalmonFile salmonFile, long maxSize) throws Exception {
        MemoryStream ms = new MemoryStream();
        SalmonStream ins = salmonFile.getInputStream();
        byte[] buffer = new byte[ENC_BUFFER_SIZE];
        int bytesRead;
        long totalBytesRead = 0;
        while ((bytesRead = ins.read(buffer, 0, buffer.length)) > 0
                && totalBytesRead < maxSize) {
            ms.write(buffer, 0, bytesRead);
            totalBytesRead += bytesRead;
        }
        ms.flush();
        ins.close();
        ms.position(0);
        return ms;
    }

    /// <summary>
    /// Create a bitmap from the unencrypted data contents of a media file
    /// If the file is a gif we get only a certain amount of data from the beginning of the file
    /// since we don't need to get the whole file.
    /// </summary>
    /// <param name="salmonFile"></param>
    /// <returns></returns>
    public static Image generateThumbnail(SalmonFile salmonFile, ImageView imageView) {

        if (cache.containsKey(salmonFile)) {
            return cache.get(salmonFile);
        }

        ThumbnailTask task = null;
        for (ThumbnailTask t : tasks) {
            if (t.file == salmonFile) {
                task = t;
                break;
            }
        }
        if (task != null) {
            // if there is an older task for this file remove it
            // since we will insert a new task to the front of the queue
            tasks.remove(task);
        }
        task = new ThumbnailTask(salmonFile, imageView);
        tasks.addFirst(task);
        executor.execute(() -> {
            try {
                ThumbnailTask task1 = tasks.take();
                generateThumbnail(task1);
            } catch (Exception e) {
                System.err.println("Could not generate thumbnail: " + e);
            }
        });
        return null;
    }

    private static Image getIcon(SalmonFile salmonFile) {
        String icon = salmonFile.isFile() ? "/icons/file.png" : "/icons/folder.png";
        Image image = null;
        if (salmonFile.isFile()) {
            try {
                String ext = SalmonFileUtils.getExtensionFromFileName(salmonFile.getBaseName()).toLowerCase();
                BufferedImage bufferedImage = ImageIO.read(Thumbnails.class.getResourceAsStream(icon));
                BufferedImage nimage = new BufferedImage(
                        bufferedImage.getWidth(),
                        bufferedImage.getHeight(),
                        BufferedImage.TYPE_INT_ARGB_PRE);
                Graphics g = nimage.getGraphics();
                Color tintColor = getFileColorFromExtension(ext);
                addImage(g, bufferedImage, tintColor);
                addText(g, ext, bufferedImage.getWidth() / 2, bufferedImage.getHeight() / 2);
                g.dispose();
                image = SwingFXUtils.toFXImage(nimage, null);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        if (image == null)
            image = new Image(Thumbnails.class.getResourceAsStream(icon));
        return image;
    }

    private static void addImage(Graphics g, BufferedImage bufferedImage, Color tintColor) {
        g.setXORMode(tintColor);
        g.drawImage(bufferedImage, 0, 0, null);
        // reset the tint
        g.setXORMode(Color.decode("#00000000"));
    }

    private static void addText(Graphics g, String text, int width, int height) {
        g.setColor(Color.WHITE);
        g.setFont(new Font("Comic sans MS", Font.BOLD, 96));
        FontMetrics fontMetrics = g.getFontMetrics();
        int textWidth = fontMetrics.stringWidth(text);
        int textHeight = fontMetrics.getHeight();
        g.drawString(text, width - textWidth / 2, height + textHeight / 4);
    }

    private static void generateThumbnail(ThumbnailTask task) {
        Image image = null;
        try {
            if (task.file.isFile() && SalmonFileUtils.isImage(task.file.getBaseName())) {
                image = Thumbnails.fromFile(task.file);
            }
            if(image == null)
                image = getIcon(task.file);
            addCache(task.file, image);
            task.view.setImage(image);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void addCache(SalmonFile file, Image image) {
        if (cacheSize > MAX_CACHE_SIZE)
            resetCache();
        cache.put(file, image);
        cacheSize += image.getWidth() * image.getHeight() * 4;
    }

    private static void resetCache() {
        cacheSize = 0;
        cache.clear();
    }

    private static Image fromFile(SalmonFile file) {
        BufferedInputStream stream = null;
        Image image = null;
        try {
            String ext = SalmonFileUtils.getExtensionFromFileName(file.getBaseName()).toLowerCase();
            if (ext.equals("gif") && file.getSize() > TMP_GIF_THUMB_MAX_SIZE)
                stream = new BufferedInputStream(new InputStreamWrapper(getTempStream(file, TMP_GIF_THUMB_MAX_SIZE)), ENC_BUFFER_SIZE);
            else
                stream = new BufferedInputStream(new InputStreamWrapper(file.getInputStream()), ENC_BUFFER_SIZE);
            image = new Image(stream, THUMBNAIL_SIZE, THUMBNAIL_SIZE, true, true);
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return image;
    }

    private static Color getFileColorFromExtension(String extension) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] bytes = extension.getBytes(Charset.defaultCharset());
        byte[] hashValue = md.digest(bytes);
        StringBuilder sb = new StringBuilder();
        sb.append(BitConverter.toHex(hashValue));
        Color color = Color.decode("#" + sb.substring(0, 6));
        color = new Color(255 - color.getRed(), 255 - color.getGreen(),
                255 - color.getBlue(), TINT_COLOR_ALPHA);
        return color;
    }
}
