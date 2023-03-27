package com.mku11.salmonfs;
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

import java.util.Comparator;

public class Comparators {
    public static Comparator<SalmonFile> defaultComparator = (SalmonFile c1, SalmonFile c2) ->
    {
        if (c1.isDirectory() && !c2.isDirectory())
            return -1;
        else if (!c1.isDirectory() && c2.isDirectory())
            return 1;
        else return 0;
    };
    public static Comparator<SalmonFile> filenameAscComparator = (SalmonFile c1, SalmonFile c2) ->
    {
        if (c1.isDirectory() && !c2.isDirectory())
            return -1;
        else if (!c1.isDirectory() && c2.isDirectory())
            return 1;
        else
            return tryGetBasename(c1).compareTo(tryGetBasename(c2));
    };
    public static Comparator<SalmonFile> filenameDescComparator = (SalmonFile c1, SalmonFile c2) ->
    {
        if (c1.isDirectory() && !c2.isDirectory())
            return 1;
        else if (!c1.isDirectory() && c2.isDirectory())
            return -1;
        else
            return tryGetBasename(c2).compareTo(tryGetBasename(c1));
    };
    public static Comparator<SalmonFile> sizeAscComparator = (SalmonFile c1, SalmonFile c2) ->
    {
        if (c1.isDirectory() && !c2.isDirectory())
            return -1;
        else if (!c1.isDirectory() && c2.isDirectory())
            return 1;
        else
            return (int) (tryGetSize(c1) - tryGetSize(c2));
    };
    public static Comparator<SalmonFile> sizeDescComparator = (SalmonFile c1, SalmonFile c2) ->
    {
        if (c1.isDirectory() && !c2.isDirectory())
            return 1;
        else if (!c1.isDirectory() && c2.isDirectory())
            return -1;
        else
            return (int) (tryGetSize(c2) - tryGetSize(c1));
    };
    public static Comparator<SalmonFile> typeAscComparator = (SalmonFile c1, SalmonFile c2) ->
    {
        if (c1.isDirectory() && !c2.isDirectory())
            return -1;
        else if (!c1.isDirectory() && c2.isDirectory())
            return 1;
        else
            return tryGetType(c1).compareTo(tryGetType(c2));
    };
    public static Comparator<SalmonFile> typeDescComparator = (SalmonFile c1, SalmonFile c2) ->
    {
        if (c1.isDirectory() && !c2.isDirectory())
            return 1;
        else if (!c1.isDirectory() && c2.isDirectory())
            return -1;
        else
            return tryGetType(c2).compareTo(tryGetType(c1));
    };
    public static Comparator<SalmonFile> dateAscComparator = (SalmonFile c1, SalmonFile c2) ->
    {
        if (c1.isDirectory() && !c2.isDirectory())
            return -1;
        else if (!c1.isDirectory() && c2.isDirectory())
            return 1;
        else
            return (int) (tryGetDate(c1) - tryGetDate(c2));
    };

    public static Comparator<SalmonFile> dateDescComparator = (SalmonFile c1, SalmonFile c2) ->
    {
        if (c1.isDirectory() && !c2.isDirectory())
            return 1;
        else if (!c1.isDirectory() && c2.isDirectory())
            return -1;
        else
            return (int) (tryGetDate(c2) - tryGetDate(c1));
    };

    public static Comparator<SalmonFile> relevanceComparator = (SalmonFile c1, SalmonFile c2) ->
            (int) c2.getTag() - (int) c1.getTag();


    private static String tryGetBasename(SalmonFile salmonFile) {
        try {
            return salmonFile.getBaseName();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return "";
    }

    private static String tryGetType(SalmonFile salmonFile) {
        try {
            if (salmonFile.isDirectory())
                return salmonFile.getBaseName();
            return SalmonDriveManager.getDrive().getExtensionFromFileName(salmonFile.getBaseName()).toLowerCase();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return "";
    }

    private static long tryGetSize(SalmonFile salmonFile) {
        try {
            if (salmonFile.isDirectory())
                return salmonFile.listFiles().length;
            return salmonFile.getSize();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return 0;
    }

    private static long tryGetDate(SalmonFile salmonFile) {
        try {
            return salmonFile.getLastDateTimeModified();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return 0;
    }
}