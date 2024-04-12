package com.mku.utils;
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

public class FileUtils {

    /**
     * Detect if filename is a text file.
     * @param filename The filename.
     * @return True if text file.
     */
    public static boolean isText(String filename) {
        String ext = getExtensionFromFileName(filename).toLowerCase();
        return ext.equals("txt");
    }

    /**
     * Detect if filename is an image file.
     * @param filename The filename.
     * @return True if image file.
     */
    public static boolean isImage(String filename) {
		String ext = getExtensionFromFileName(filename).toLowerCase();
		return ext.equals("png") || ext.equals("jpg") || ext.equals("jpeg") || ext.equals("bmp") 
            || ext.equals("webp") || ext.equals("gif") || ext.equals("tif") || ext.equals("tiff");
    }

    /**
     * Detect if filename is an audio file.
     * @param filename The filename.
     * @return True if audio file.
     */
    public static boolean isAudio(String filename) {
        String ext = getExtensionFromFileName(filename).toLowerCase();
        return ext.equals("wav") || ext.equals("mp3");
    }

    /**
     * Detect if filename is a video file.
     * @param filename The filename.
     * @return True if video file.
     */
    public static boolean isVideo(String filename) {
        String ext = getExtensionFromFileName(filename).toLowerCase();
        return ext.equals("mp4");
    }

    /**
     * Detect if filename is a pdf file.
     * @param filename The file name
     * @return True if pdf
     */
    public static boolean isPdf(String filename)
    {
        String ext = getExtensionFromFileName(filename).toLowerCase();
        return ext.equals("pdf");
    }

    /**
     * Return the extension of a filename.
     *
     * @param fileName The file name
     * @return The file extension
     */
    public static String getExtensionFromFileName(String fileName) {
        if (fileName == null)
            return "";
        int index = fileName.lastIndexOf(".");
        if (index >= 0) {
            return fileName.substring(index + 1);
        } else
            return "";
    }

    /**
     * Return a filename without extension
     *
     * @param fileName The file name
     * @return The file name without extension
     */
    public static String getFileNameWithoutExtension(String fileName) {
        if (fileName == null)
            return "";
        int index = fileName.lastIndexOf(".");
        if (index >= 0) {
            return fileName.substring(0, index);
        } else
            return "";
    }

}
