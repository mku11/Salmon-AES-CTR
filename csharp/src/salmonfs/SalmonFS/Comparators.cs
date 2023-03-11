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
using System;
using System.Collections.Generic;

namespace Salmon.FS
{
    public class Comparators
    {
        public static Comparison<SalmonFile> defaultComparison = (SalmonFile c1, SalmonFile c2) =>
        {
            if (c1.IsDirectory() && !c2.IsDirectory())
                return -1;
            else if (!c1.IsDirectory() && c2.IsDirectory())
                return 1;
            else return 0;
        };

        public static Comparison<SalmonFile> filenameComparison = (SalmonFile c1, SalmonFile c2) =>
        {
            if (c1.IsDirectory() && !c2.IsDirectory())
                return -1;
            else if (!c1.IsDirectory() && c2.IsDirectory())
                return 1;
            else
                return tryGetBasename(c1).CompareTo(tryGetBasename(c2));
        };
        public static Comparison<SalmonFile> sizeComparison = (SalmonFile c1, SalmonFile c2) =>
        {
            if (c1.IsDirectory() && !c2.IsDirectory())
                return -1;
            else if (!c1.IsDirectory() && c2.IsDirectory())
                return 1;
            else
                return (int)(tryGetSize(c1) - tryGetSize(c2));
        };
        public static Comparison<SalmonFile> typeComparison = (SalmonFile c1, SalmonFile c2) =>
        {
            if (c1.IsDirectory() && !c2.IsDirectory())
                return -1;
            else if (!c1.IsDirectory() && c2.IsDirectory())
                return 1;
            else
                return tryGetType(c1).CompareTo(tryGetType(c2));
        };
        public static Comparison<SalmonFile> dateComparison = (SalmonFile c1, SalmonFile c2) =>
        {
            if (c1.IsDirectory() && !c2.IsDirectory())
                return -1;
            else if (!c1.IsDirectory() && c2.IsDirectory())
                return 1;
            else
                return (int)(tryGetDate(c1) - tryGetDate(c2));
        };

        public static Comparison<SalmonFile> relevanceComparison = (SalmonFile c1, SalmonFile c2) =>
                (int)c2.GetTag() - (int)c1.GetTag();


        private static string tryGetBasename(SalmonFile salmonFile)
        {
            try
            {
                return salmonFile.GetBaseName();
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine(ex);
            }
            return "";
        }

        private static string tryGetType(SalmonFile salmonFile)
        {
            try
            {
                if (salmonFile.IsDirectory())
                    return salmonFile.GetBaseName();
                return SalmonDriveManager.GetDrive().GetExtensionFromFileName(salmonFile.GetBaseName()).ToLower();
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine(ex);
            }
            return "";
        }

        private static long tryGetSize(SalmonFile salmonFile)
        {
            try
            {
                if (salmonFile.IsDirectory())
                    return salmonFile.ListFiles().Length;
                return salmonFile.GetSize();
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine(ex);
            }
            return 0;
        }

        private static long tryGetDate(SalmonFile salmonFile)
        {
            try
            {
                return salmonFile.GetLastDateTimeModified();
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine(ex);
            }
            return 0;
        }
    }
}