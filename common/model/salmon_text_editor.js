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


import {MemoryStream} from "../../lib/salmon-core/io/memory_stream.js";
import {SalmonDialog } from "../../vault/dialog/salmon_dialog.js";

export class SalmonTextEditor
{
    async onSave(file,text)
    {
        let targetFile = null;
        let stream = null;
        let ins = null;
        let success = false;
        try
        {
            let contents = new TextEncoder().encode(text);
            ins = new MemoryStream(contents);
            let dir = await file.getParent();
            targetFile = await dir.createFile(await file.getBaseName());
            stream = await targetFile.getOutputStream();
            await ins.copyTo(stream);
            await stream.flush();
            success = true;
        }
        catch (ex)
        {
            console.error(ex);
            SalmonDialog.promptDialog("Error", "Error during saving file: " + ex);
        }
        finally
        {
            if (success)
            {
                if (file != null)
                    await file.delete();
            }
            else
            {
                if (targetFile != null)
                    await targetFile.delete();
            }
            if (stream != null)
            {
                try
                {
                    await stream.close();
                }
                catch (ignored)
                {
                }
            }
            if (ins != null)
            {
                try
                {
                    await ins.close();
                }
                catch (ignored)
                {
                }
            }
        }
        if (success)
            return targetFile;
        return null;
    }

    async getTextContent(file) {
        let stream = await file.getInputStream();
        let ms = new MemoryStream();
        await stream.copyTo(ms);
        await stream.close();
        let bytes = ms.toArray();
        let content = new TextDecoder().decode(bytes);
        return content;
    }
}