package com.mku.salmon.win.registry;
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

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;

/**
 * Read and Write to Windows registry
 */
public class SalmonRegistry
{
    private static String CATEGORY = "Software";
    private static String APP_NAME = "Salmon";
    private static String SETTINGS_KEY = "Settings";
	private static String SETTINGS_PATH = CATEGORY + "\\" + APP_NAME + "\\" + SETTINGS_KEY;

    public SalmonRegistry()
    {
        Advapi32Util.registryCreateKey(WinReg.HKEY_CURRENT_USER, CATEGORY + "\\" + APP_NAME);
        Advapi32Util.registryCreateKey(WinReg.HKEY_CURRENT_USER, CATEGORY + "\\" + APP_NAME + "\\" + SETTINGS_KEY);
    }

    /**
     * Read a value from the registry
     * @param key
     * @return
     */
    @SuppressWarnings("unchecked")
    public <T> T read(String key)
    {
        return (T) Advapi32Util.registryGetValue(WinReg.HKEY_CURRENT_USER, SETTINGS_PATH, key);
    }

    /**
     * Write a value from the registry
     * @param key
     * @param value
     */
    public void write(String key, Object value)
    {
        if(value instanceof String)
            Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, SETTINGS_PATH, key, (String) value);
        else if(value instanceof byte[])
            Advapi32Util.registrySetBinaryValue(WinReg.HKEY_CURRENT_USER, SETTINGS_PATH, key, (byte[]) value);
        else if(value instanceof Integer)
            Advapi32Util.registrySetIntValue(WinReg.HKEY_CURRENT_USER, SETTINGS_PATH, key, (int) value);
        else
            throw new UnsupportedOperationException("Data type not supported");
    }
	
	
	/// <summary>
    /// Delete a value
    /// </summary>
    /// <param name="key"></param>

    /**
     * Delete a value
     * @param key
     */
    public void delete(String key)
    {
        Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, SETTINGS_PATH, key);
    }

    /**
     * True if value exists
     * @param key
     * @return
     */
    public boolean exists(String key){
        return Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, SETTINGS_PATH, key);
    }

}