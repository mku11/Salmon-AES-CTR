package com.mku.salmonfs;
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

import com.mku.utils.SalmonFileUtils;

import java.util.Comparator;

/**
 * Useful comparators for SalmonFile.
 */
public class SalmonFileComparators {
    private static final Comparator<SalmonFile> defaultComparator = (SalmonFile c1, SalmonFile c2) ->
    {
        if (c1.isDirectory() && !c2.isDirectory())
            return -1;
        else if (!c1.isDirectory() && c2.isDirectory())
            return 1;
        else return 0;
    };

    private static final Comparator<SalmonFile> filenameAscComparator = (SalmonFile c1, SalmonFile c2) ->
    {
        if (c1.isDirectory() && !c2.isDirectory())
            return -1;
        else if (!c1.isDirectory() && c2.isDirectory())
            return 1;
        else
            return tryGetBasename(c1).compareTo(tryGetBasename(c2));
    };

    private static final Comparator<SalmonFile> filenameDescComparator = (SalmonFile c1, SalmonFile c2) ->
    {
        if (c1.isDirectory() && !c2.isDirectory())
            return 1;
        else if (!c1.isDirectory() && c2.isDirectory())
            return -1;
        else
            return tryGetBasename(c2).compareTo(tryGetBasename(c1));
    };

    private static final Comparator<SalmonFile> sizeAscComparator = (SalmonFile c1, SalmonFile c2) ->
    {
        if (c1.isDirectory() && !c2.isDirectory())
            return -1;
        else if (!c1.isDirectory() && c2.isDirectory())
            return 1;
        else
            return (int) (tryGetSize(c1) - tryGetSize(c2));
    };

    private static final Comparator<SalmonFile> sizeDescComparator = (SalmonFile c1, SalmonFile c2) ->
    {
        if (c1.isDirectory() && !c2.isDirectory())
            return 1;
        else if (!c1.isDirectory() && c2.isDirectory())
            return -1;
        else
            return (int) (tryGetSize(c2) - tryGetSize(c1));
    };

    private static final Comparator<SalmonFile> typeAscComparator = (SalmonFile c1, SalmonFile c2) ->
    {
        if (c1.isDirectory() && !c2.isDirectory())
            return -1;
        else if (!c1.isDirectory() && c2.isDirectory())
            return 1;
        else
            return tryGetType(c1).compareTo(tryGetType(c2));
    };

    private static final Comparator<SalmonFile> typeDescComparator = (SalmonFile c1, SalmonFile c2) ->
    {
        if (c1.isDirectory() && !c2.isDirectory())
            return 1;
        else if (!c1.isDirectory() && c2.isDirectory())
            return -1;
        else
            return tryGetType(c2).compareTo(tryGetType(c1));
    };

    private static final Comparator<SalmonFile> dateAscComparator = (SalmonFile c1, SalmonFile c2) ->
    {
        if (c1.isDirectory() && !c2.isDirectory())
            return -1;
        else if (!c1.isDirectory() && c2.isDirectory())
            return 1;
        else
            return (int) (tryGetDate(c1) - tryGetDate(c2));
    };

    private static final Comparator<SalmonFile> dateDescComparator = (SalmonFile c1, SalmonFile c2) ->
    {
        if (c1.isDirectory() && !c2.isDirectory())
            return 1;
        else if (!c1.isDirectory() && c2.isDirectory())
            return -1;
        else
            return (int) (tryGetDate(c2) - tryGetDate(c1));
    };

    private static final Comparator<SalmonFile> relevanceComparator = (SalmonFile c1, SalmonFile c2) ->
            (int) c2.getTag() - (int) c1.getTag();

    /**
     * Get default comparator. This is the fastest sorting comparator as it simply lists folders first.
     * The rest of the files will be listed without sorting.
     * @return
     */
    public static Comparator<SalmonFile> getDefaultComparator() {
        return defaultComparator;
    }

    /**
     * Get Filename Ascending Comparator.
     * @return
     */
    public static Comparator<SalmonFile> getFilenameAscComparator() {
        return filenameAscComparator;
    }

    /**
     * Get Filename Descending Comparator.
     * @return
     */
    public static Comparator<SalmonFile> getFilenameDescComparator() {
        return filenameDescComparator;
    }

    /**
     * Get Size Ascending Comparator.
     * @return
     */
    public static Comparator<SalmonFile> getSizeAscComparator() {
        return sizeAscComparator;
    }

    /**
     * Get Size Descending Comparator.
     * @return
     */
    public static Comparator<SalmonFile> getSizeDescComparator() {
        return sizeDescComparator;
    }

    /**
     * Get File Type Ascending Comparator.
     * @return
     */
    public static Comparator<SalmonFile> getTypeAscComparator() {
        return typeAscComparator;
    }

    /**
     * Get File Type Descending Comparator.
     * @return
     */
    public static Comparator<SalmonFile> getTypeDescComparator() {
        return typeDescComparator;
    }

    /**
     * Get Date Ascending Comparator.
     * @return
     */
    public static Comparator<SalmonFile> getDateAscComparator() {
        return dateAscComparator;
    }

    /**
     * Get Date Descending Comparator.
     * @return
     */
    public static Comparator<SalmonFile> getDateDescComparator() {
        return dateDescComparator;
    }

    /**
     * Get Relevant Comparator. This will sort by the search result relevancy in the Tag member. Most relevant results
     * will be listed first.
     * @return
     */
    public static Comparator<SalmonFile> getRelevanceComparator() {
        return relevanceComparator;
    }

    /**
     * Get the SalmonFile basename if available.
     * @param salmonFile
     * @return
     */
    private static String tryGetBasename(SalmonFile salmonFile) {
        try {
            return salmonFile.getBaseName();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return "";
    }

    /**
     * Get the SalmonFile file type extension if available.
     * @param salmonFile
     * @return
     */
    private static String tryGetType(SalmonFile salmonFile) {
        try {
            if (salmonFile.isDirectory())
                return salmonFile.getBaseName();
            return SalmonFileUtils.getExtensionFromFileName(salmonFile.getBaseName()).toLowerCase();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return "";
    }

    /**
     * Get the SalmonFile size if available.
     * @param salmonFile
     * @return
     */
    private static long tryGetSize(SalmonFile salmonFile) {
        try {
            if (salmonFile.isDirectory())
                return salmonFile.getChildrenCount();
            // the original file length requires reading the chunks size
            // from the file so it can get a bit expensive
            // so instead we sort on the real file size
            return salmonFile.getRealFile().length();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return 0;
    }

    /**
     * Get the SalmonFile date if available.
     * @param salmonFile
     * @return
     */
    private static long tryGetDate(SalmonFile salmonFile) {
        try {
            return salmonFile.getLastDateTimeModified();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return 0;
    }
}