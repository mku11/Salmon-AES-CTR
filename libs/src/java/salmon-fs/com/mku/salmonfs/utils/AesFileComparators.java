package com.mku.salmonfs.utils;
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

import com.mku.salmonfs.file.AesFile;
import com.mku.fs.drive.utils.FileUtils;

import java.util.Comparator;

/**
 * Useful comparators for SalmonFile.
 */
public class AesFileComparators {
    private static final Comparator<AesFile> defaultComparator = (AesFile c1, AesFile c2) ->
    {
        if (c1.isDirectory() && !c2.isDirectory())
            return -1;
        else if (!c1.isDirectory() && c2.isDirectory())
            return 1;
        else return 0;
    };

    private static final Comparator<AesFile> filenameAscComparator = (AesFile c1, AesFile c2) ->
    {
        if (c1.isDirectory() && !c2.isDirectory())
            return -1;
        else if (!c1.isDirectory() && c2.isDirectory())
            return 1;
        else
            return tryGetBasename(c1).compareTo(tryGetBasename(c2));
    };

    private static final Comparator<AesFile> filenameDescComparator = (AesFile c1, AesFile c2) ->
    {
        if (c1.isDirectory() && !c2.isDirectory())
            return 1;
        else if (!c1.isDirectory() && c2.isDirectory())
            return -1;
        else
            return tryGetBasename(c2).compareTo(tryGetBasename(c1));
    };

    private static final Comparator<AesFile> sizeAscComparator = (AesFile c1, AesFile c2) ->
    {
        if (c1.isDirectory() && !c2.isDirectory())
            return -1;
        else if (!c1.isDirectory() && c2.isDirectory())
            return 1;
        else
            return (int) (tryGetSize(c1) - tryGetSize(c2));
    };

    private static final Comparator<AesFile> sizeDescComparator = (AesFile c1, AesFile c2) ->
    {
        if (c1.isDirectory() && !c2.isDirectory())
            return 1;
        else if (!c1.isDirectory() && c2.isDirectory())
            return -1;
        else
            return (int) (tryGetSize(c2) - tryGetSize(c1));
    };

    private static final Comparator<AesFile> typeAscComparator = (AesFile c1, AesFile c2) ->
    {
        if (c1.isDirectory() && !c2.isDirectory())
            return -1;
        else if (!c1.isDirectory() && c2.isDirectory())
            return 1;
        else
            return tryGetType(c1).compareTo(tryGetType(c2));
    };

    private static final Comparator<AesFile> typeDescComparator = (AesFile c1, AesFile c2) ->
    {
        if (c1.isDirectory() && !c2.isDirectory())
            return 1;
        else if (!c1.isDirectory() && c2.isDirectory())
            return -1;
        else
            return tryGetType(c2).compareTo(tryGetType(c1));
    };

    private static final Comparator<AesFile> dateAscComparator = (AesFile c1, AesFile c2) ->
    {
        if (c1.isDirectory() && !c2.isDirectory())
            return -1;
        else if (!c1.isDirectory() && c2.isDirectory())
            return 1;
        else
            return (int) (tryGetDate(c1) - tryGetDate(c2));
    };

    private static final Comparator<AesFile> dateDescComparator = (AesFile c1, AesFile c2) ->
    {
        if (c1.isDirectory() && !c2.isDirectory())
            return 1;
        else if (!c1.isDirectory() && c2.isDirectory())
            return -1;
        else
            return (int) (tryGetDate(c2) - tryGetDate(c1));
    };

    private static final Comparator<AesFile> relevanceComparator = (AesFile c1, AesFile c2) ->
            (int) c2.getTag() - (int) c1.getTag();

    /**
     * Get default comparator. This is the fastest sorting comparator as it simply lists folders first.
     * The rest of the files will be listed without sorting.
     * @return The default comparator
     */
    public static Comparator<AesFile> getDefaultComparator() {
        return defaultComparator;
    }

    /**
     * Get Filename Ascending Comparator.
     * @return The filename scending comparator.
     */
    public static Comparator<AesFile> getFilenameAscComparator() {
        return filenameAscComparator;
    }

    /**
     * Get Filename Descending Comparator.
     * @return filename descending comparator
     */
    public static Comparator<AesFile> getFilenameDescComparator() {
        return filenameDescComparator;
    }

    /**
     * Get Size Ascending Comparator.
     * @return The size ascending comparator
     */
    public static Comparator<AesFile> getSizeAscComparator() {
        return sizeAscComparator;
    }

    /**
     * Get Size Descending Comparator.
     * @return The size descending comparator
     */
    public static Comparator<AesFile> getSizeDescComparator() {
        return sizeDescComparator;
    }

    /**
     * Get File Type Ascending Comparator.
     * @return The file type ascending comparator
     */
    public static Comparator<AesFile> getTypeAscComparator() {
        return typeAscComparator;
    }

    /**
     * Get File Type Descending Comparator.
     * @return The file type descending comparator
     */
    public static Comparator<AesFile> getTypeDescComparator() {
        return typeDescComparator;
    }

    /**
     * Get Date Ascending Comparator.
     * @return The date ascending comparator
     */
    public static Comparator<AesFile> getDateAscComparator() {
        return dateAscComparator;
    }

    /**
     * Get Date Descending Comparator.
     * @return The date descending comparator
     */
    public static Comparator<AesFile> getDateDescComparator() {
        return dateDescComparator;
    }

    /**
     * Get Relevant Comparator. This will sort by the search result relevancy in the Tag member. Most relevant results
     * will be listed first.
     * @return The relevancy comparator
     */
    public static Comparator<AesFile> getRelevanceComparator() {
        return relevanceComparator;
    }

    /**
     * Get the SalmonFile basename if available.
     * @param aesFile The file
     * @return The base name
     */
    private static String tryGetBasename(AesFile aesFile) {
        try {
            return aesFile.getBaseName();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return "";
    }

    /**
     * Get the SalmonFile file type extension if available.
     * @param aesFile The Aes file to get the file type from
     * @return The file type
     */
    private static String tryGetType(AesFile aesFile) {
        try {
            if (aesFile.isDirectory())
                return aesFile.getBaseName();
            return FileUtils.getExtensionFromFileName(aesFile.getBaseName()).toLowerCase();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return "";
    }

    /**
     * Get the SalmonFile size if available.
     * @param aesFile The file
     * @return The size
     */
    private static long tryGetSize(AesFile aesFile) {
        try {
            if (aesFile.isDirectory())
                return aesFile.getChildrenCount();
            // the original file length requires reading the chunks size
            // from the file so it can get a bit expensive
            // so instead we sort on the real file size
            return aesFile.getRealFile().length();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return 0;
    }

    /**
     * Get the SalmonFile date if available.
     * @param aesFile The file
     * @return The date in milliseconds
     */
    private static long tryGetDate(AesFile aesFile) {
        try {
            return aesFile.getLastDateTimeModified();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return 0;
    }
}