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
namespace Salmon.Win.Registry;

using Microsoft.Win32;
using System;

/// <summary>
/// Read and Write to Windows registry
/// </summary>
public class SalmonRegistry
{
    private static string CATEGORY = "Software";
    private static string APP_NAME = "Salmon";
    private static string SETTINGS_KEY = "Settings";

    private RegistryKey rkey;

#pragma warning disable CA1416 // Validate platform compatibility

    /// <summary>
    /// Maps to the windows registry that can be used to store a key/value pair.
    /// </summary>
    public SalmonRegistry()
    {
        rkey = Registry.CurrentUser.OpenSubKey(CATEGORY, true);
        rkey.CreateSubKey(APP_NAME);
        rkey = rkey.OpenSubKey(APP_NAME, true);

        rkey.CreateSubKey(SETTINGS_KEY);
        rkey = rkey.OpenSubKey(SETTINGS_KEY, true);
    }

    /// <summary>
    /// Read a value from the registry
    /// </summary>
    /// <param name="key">The key</param>
    /// <returns>The value</returns>
    public object Read(string key)
    {
        return rkey.GetValue(key);
    }

    /// <summary>
    /// Write a value from the registry
    /// </summary>
    /// <param name="key">The key</param>
    /// <param name="value">The value</param>
    public void Write(string key, object value)
    {
        rkey.SetValue(key, value);
    }


    /// <summary>
    /// Delete a value
    /// </summary>
    /// <param name="key">The key</param>
    public void Delete(string key)
    {
        rkey.DeleteValue(key);
    }
	
	/// <summary>
    /// True if value exists
    /// </summary>
    /// <param name="key">The key</param>
	public bool Exists(string key){
        return rkey.GetValue(key) != null;
    }
#pragma warning restore CA1416 // Validate platform compatibility
}