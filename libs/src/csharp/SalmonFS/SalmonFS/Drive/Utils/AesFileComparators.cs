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

using Mku.FS.Drive.Utils;
using Mku.SalmonFS.File;
using System;
using System.Collections.Generic;

namespace Mku.SalmonFS.Utils;

/// <summary>
///  Useful Comparers for SalmonFile.
/// </summary>
public class SalmonFileComparators
{

    /// <summary>
    ///  Get default Comparer. This is the fastest sorting Comparer as it simply lists folders first.
    ///  The rest of the files will be listed without sorting.
    /// </summary>
    ///  <returns>The default comparer</returns>
    public static Comparer<SalmonFile> DefaultComparator { get; } = 
        Comparer<SalmonFile>.Create((SalmonFile c1, SalmonFile c2) =>
    {
        if (c1.IsDirectory && !c2.IsDirectory)
            return -1;
        else if (!c1.IsDirectory && c2.IsDirectory)
            return 1;
        else return 0;
    });

    /// <summary>
    ///  Get Filename Ascending Comparer.
	/// </summary>
	///  <returns>The filename ascending comparer</returns>
    public static Comparer<SalmonFile> FilenameAscComparator { get; } = 
        Comparer<SalmonFile>.Create((SalmonFile c1, SalmonFile c2) =>
    {
        if (c1.IsDirectory && !c2.IsDirectory)
            return -1;
        else if (!c1.IsDirectory && c2.IsDirectory)
            return 1;
        else
            return TryGetBasename(c1).CompareTo(TryGetBasename(c2));
    });

    /// <summary>
    ///  Get Filename Descending Comparer.
	/// </summary>
	///  <returns>The filename descending comparer</returns>
    public static Comparer<SalmonFile> FilenameDescComparator { get; } = 
        Comparer<SalmonFile>.Create((SalmonFile c1, SalmonFile c2) =>
    {
        if (c1.IsDirectory && !c2.IsDirectory)
            return 1;
        else if (!c1.IsDirectory && c2.IsDirectory)
            return -1;
        else
            return TryGetBasename(c2).CompareTo(TryGetBasename(c1));
    });

    /// <summary>
    ///  Get Size Ascending Comparer.
	/// </summary>
	///  <returns>The size ascending comparer</returns>
    public static Comparer<SalmonFile> SizeAscComparator { get; } = 
        Comparer<SalmonFile>.Create((SalmonFile c1, SalmonFile c2) =>
    {
        if (c1.IsDirectory && !c2.IsDirectory)
            return -1;
        else if (!c1.IsDirectory && c2.IsDirectory)
            return 1;
        else
            return (int)(TryGetSize(c1) - TryGetSize(c2));
    });

    /// <summary>
    ///  Get Size Descending Comparer.
	/// </summary>
	///  <returns>The size descending comparer</returns>
    public static Comparer<SalmonFile> SizeDescComparator { get; } = 
        Comparer<SalmonFile>.Create((SalmonFile c1, SalmonFile c2) =>
    {
        if (c1.IsDirectory && !c2.IsDirectory)
            return 1;
        else if (!c1.IsDirectory && c2.IsDirectory)
            return -1;
        else
            return (int)(TryGetSize(c2) - TryGetSize(c1));
    });

    /// <summary>
    ///  Get File Type Ascending Comparer.
	/// </summary>
	///  <returns>The file type ascending comparer</returns>
    public static Comparer<SalmonFile> TypeAscComparator { get; } = 
        Comparer<SalmonFile>.Create((SalmonFile c1, SalmonFile c2) =>
    {
        if (c1.IsDirectory && !c2.IsDirectory)
            return -1;
        else if (!c1.IsDirectory && c2.IsDirectory)
            return 1;
        else
            return TryGetType(c1).CompareTo(TryGetType(c2));
    });

    /// <summary>
    ///  Get Filetype Descending Comparer.
	/// </summary>
	///  <returns>The file type descending comparer</returns>
    public static Comparer<SalmonFile> TypeDescComparator { get; } =
        Comparer<SalmonFile>.Create((SalmonFile c1, SalmonFile c2) =>
    {
        if (c1.IsDirectory && !c2.IsDirectory)
            return 1;
        else if (!c1.IsDirectory && c2.IsDirectory)
            return -1;
        else
            return TryGetType(c2).CompareTo(TryGetType(c1));
    });

    /// <summary>
    ///  Get Date Ascending Comparer.
	/// </summary>
	///  <returns>The date ascending comparer</returns>
    public static Comparer<SalmonFile> DateAscComparator { get; } = 
        Comparer<SalmonFile>.Create((SalmonFile c1, SalmonFile c2) =>
    {
        if (c1.IsDirectory && !c2.IsDirectory)
            return -1;
        else if (!c1.IsDirectory && c2.IsDirectory)
            return 1;
        else
            return (int)(TryGetDate(c1) - TryGetDate(c2));
    });

    /// <summary>
    ///  Get Date Descending Comparer.
	/// </summary>
	///  <returns>The date descending comparer</returns>
    public static Comparer<SalmonFile> DateDescComparator { get; } = 
        Comparer<SalmonFile>.Create((SalmonFile c1, SalmonFile c2) =>
    {
        if (c1.IsDirectory && !c2.IsDirectory)
            return 1;
        else if (!c1.IsDirectory && c2.IsDirectory)
            return -1;
        else
            return (int)(TryGetDate(c2) - TryGetDate(c1));
    });

    /// <summary>
    ///  Get Relevant Comparer. This will sort by the search result relevancy in the Tag member. Most relevant results
    ///  will be listed first.
	/// </summary>
	///  <returns>The relevance comparer</returns>
    public static Comparer<SalmonFile> RelevanceComparator { get; } = 
        Comparer<SalmonFile>.Create((SalmonFile c1, SalmonFile c2) =>
            (int)c2.Tag - (int)c1.Tag);


    /// <summary>
    ///  Get the SalmonFile base name if available.
    /// </summary>
    ///  <param name="salmonFile">The file</param>
    ///  <returns>The base name</returns>
    private static string TryGetBasename(SalmonFile salmonFile)
    {
        try
        {
            return salmonFile.BaseName;
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
        }
        return "";
    }

    /// <summary>
    ///  Get the SalmonFile file type extension if available.
	/// </summary>
	///  <param name="salmonFile">The file</param>
    ///  <returns>The file type</returns>
    private static string TryGetType(SalmonFile salmonFile)
    {
        try
        {
            if (salmonFile.IsDirectory)
                return salmonFile.BaseName;
            return FileUtils.GetExtensionFromFileName(salmonFile.BaseName).ToLower();
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
        }
        return "";
    }

    /// <summary>
    ///  Get the SalmonFile size if available.
	/// </summary>
	///  <param name="salmonFile">The file</param>
    ///  <returns>The file size</returns>
    private static long TryGetSize(SalmonFile salmonFile)
    {
        try
        {
            if (salmonFile.IsDirectory)
                return salmonFile.ChildrenCount;
            // the original file length requires reading the chunks size
            // from the file so it can get a bit expensive
            // so instead we sort on the real file size
            return salmonFile.RealFile.Length;
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
        }
        return 0;
    }

    /// <summary>
    ///  Get the SalmonFile date if available.
	/// </summary>
	///  <param name="salmonFile">The file</param>
    ///  <returns>The file date in milliseconds</returns>
    private static long TryGetDate(SalmonFile salmonFile)
    {
        try
        {
            return salmonFile.LastDateTimeModified;
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
        }
        return 0;
    }
}