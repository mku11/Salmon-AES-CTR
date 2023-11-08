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

using Mku.Salmon.IO;
using Mku.SalmonFS;
using Salmon.Vault.Dialog;
using System;
using System.IO;
using System.Runtime.CompilerServices;
using System.Text;

namespace Salmon.Vault.Model;

public class SalmonTextEditor
{
    [MethodImpl(MethodImplOptions.Synchronized)]
    public SalmonFile OnSave(SalmonFile file, string text)
    {
        SalmonFile targetFile = null;
        SalmonStream stream = null;
        MemoryStream ins = null;
        bool success = false;
        try
        {
            byte[] contents = UTF8Encoding.UTF8.GetBytes(text);
            ins = new MemoryStream(contents);
            SalmonFile dir = file.Parent;
            targetFile = dir.CreateFile(file.BaseName);
            stream = targetFile.GetOutputStream();
            ins.CopyTo(stream);
            stream.Flush();
            success = true;
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
            SalmonDialog.PromptDialog("Error", "Error during saving file: " + ex.Message);
        }
        finally
        {
            if (success)
            {
                if (file != null)
                    file.Delete();
            }
            else
            {
                if (targetFile != null)
                    targetFile.Delete();
            }
            if (stream != null)
            {
                try
                {
                    stream.Close();
                }
                catch (IOException ignored)
                {
                }
            }
            if (ins != null)
            {
                try
                {
                    ins.Close();
                }
                catch (IOException ignored)
                {
                }
            }
        }
        if (success)
            return targetFile;
        return null;
    }

    [MethodImpl(MethodImplOptions.Synchronized)]
    public string GetTextContent(SalmonFile file)
    {
        SalmonStream stream = file.GetInputStream();
        MemoryStream ms = new MemoryStream();
        stream.CopyTo(ms);
        stream.Close();
        byte[] bytes = ms.ToArray();
        string content = UTF8Encoding.UTF8.GetString(bytes);
        return content;
    }
}