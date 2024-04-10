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
/**
 * Copy contents of a file to another file.
 *
 * @param src              The source directory
 * @param dest             The target directory
 * @param delete           True to delete the source files when complete
 * @param progressListener The progress listener
 * @return
 * @throws IOException Thrown if there is an IO error.
 */
export async function copyFileContents(src, dest, deleteAfter, progressListener) {
    let source = await src.getInputStream();
    let target = await dest.getOutputStream();
    try {
        await source.copyTo(target, 0, progressListener);
    }
    catch (ex) {
        await dest.delete();
        return false;
    }
    finally {
        await source.close();
        await target.close();
    }
    if (deleteAfter)
        await src.delete();
    return true;
}
/**
 * Copy a directory recursively
 *
 * @param {IRealFile} src Source directory
 * @param {IRealFile} destDir Destination directory to copy into.
 * @param {((realfile: IRealFile, position: number, length: number) => void) | null} progressListener Progress listener
 * @param {((realfile: IRealFile) => Promise<string>) | null} autoRename Autorename callback (default is none).
 * @param {boolean} autoRenameFolders Apply autorename to folders also (default is true)
 * @param {((realfile: IRealFile, ex: Error) => void) | null} onFailed OnFailed callback
 * @throws IOException Thrown if there is an IO error.
 */
export async function copyRecursively(src, destDir, progressListener = null, autoRename = null, autoRenameFolders = true, onFailed = null) {
    let newFilename = src.getBaseName();
    let newFile;
    newFile = await destDir.getChild(newFilename);
    if (await src.isFile()) {
        if (newFile != null && await newFile.exists()) {
            if (autoRename != null) {
                newFilename = await autoRename(src);
            }
            else {
                if (onFailed != null)
                    onFailed(src, new Error("Another file exists"));
                return;
            }
        }
        await src.copy(destDir, newFilename, (position, length) => {
            if (progressListener != null) {
                progressListener(src, position, length);
            }
        });
    }
    else if (await src.isDirectory()) {
        if (progressListener != null)
            progressListener(src, 0, 1);
        if (destDir.getAbsolutePath().startsWith(src.getAbsolutePath())) {
            if (progressListener != null)
                progressListener(src, 1, 1);
            return;
        }
        if (newFile != null && await newFile.exists() && autoRename != null && autoRenameFolders)
            newFile = await destDir.createDirectory(await autoRename(src));
        else if (newFile == null || !await newFile.exists())
            newFile = await destDir.createDirectory(newFilename);
        if (progressListener != null)
            progressListener(src, 1, 1);
        for (let child of await src.listFiles()) {
            if (newFile == null)
                throw new Error("Could not get new file");
            await copyRecursively(child, newFile, progressListener, autoRename, autoRenameFolders, onFailed);
        }
    }
}
/**
 * Move a directory recursively
 *
 * @param {IRealFile} src Source directory
 * @param {IRealFile} destDir Destination directory to move into.
 * @param {((realfile: IRealFile, position: number, length: number) => void) | null} progressListener Progress listener
 * @param {((realfile: IRealFile) => Promise<string>) | null} autoRename Autorename callback (default is none).
 * @param {boolean} autoRenameFolders Apply autorename to folders also (default is true)
 * @param {((realfile: IRealFile, ex: Error) => void) | null} onFailed OnFailed callback
 */
export async function moveRecursively(file, destDir, progressListener = null, autoRename = null, autoRenameFolders = true, onFailed = null) {
    // target directory is the same
    let parent = await file.getParent();
    if (parent != null && parent.getAbsolutePath() == destDir.getAbsolutePath()) {
        if (progressListener != null) {
            progressListener(file, 0, 1);
            progressListener(file, 1, 1);
        }
        return;
    }
    let newFilename = file.getBaseName();
    let newFile;
    newFile = await destDir.getChild(newFilename);
    if (await file.isFile()) {
        if (newFile != null && await newFile.exists()) {
            if (newFile.getAbsolutePath() == file.getAbsolutePath())
                return;
            if (autoRename != null) {
                newFilename = await autoRename(file);
            }
            else {
                if (onFailed != null)
                    onFailed(file, new Error("Another file exists"));
                return;
            }
        }
        await file.move(destDir, newFilename, (position, length) => {
            if (progressListener != null) {
                progressListener(file, position, length);
            }
        });
    }
    else if (await file.isDirectory()) {
        if (progressListener != null)
            progressListener(file, 0, 1);
        if (destDir.getAbsolutePath().startsWith(file.getAbsolutePath())) {
            if (progressListener != null)
                progressListener(file, 1, 1);
            return;
        }
        if ((newFile != null && await newFile.exists() && autoRename != null && autoRenameFolders)
            || newFile == null || !await newFile.exists()) {
            if (autoRename != null)
                newFile = await file.move(destDir, await autoRename(file), null);
            return;
        }
        if (progressListener != null)
            progressListener(file, 1, 1);
        for (let child of await file.listFiles()) {
            if (newFile == null)
                throw new Error("Could not get new file");
            await moveRecursively(child, newFile, progressListener, autoRename, autoRenameFolders, onFailed);
        }
        if (!await file.delete()) {
            if (onFailed != null)
                onFailed(file, new Error("Could not delete source directory"));
            return;
        }
    }
}
/**
 * Delete a directory recursively
 * @param progressListener The progress listener
 * @param onFailed Callback when delete fails
 */
export async function deleteRecursively(file, progressListener, onFailed) {
    if (await file.isFile()) {
        progressListener(file, 0, 1);
        if (!file.delete()) {
            if (onFailed != null)
                onFailed(file, new Error("Could not delete file"));
            return;
        }
        progressListener(file, 1, 1);
    }
    else if (await file.isDirectory()) {
        for (let child of await file.listFiles()) {
            await deleteRecursively(child, progressListener, onFailed);
        }
        if (await !file.delete()) {
            if (onFailed != null)
                onFailed(file, new Error("Could not delete directory"));
            return;
        }
    }
}
/**
 * Get an auto generated copy of the name for a file.
 */
export async function autoRenameFile(file) {
    return autoRename(file.getBaseName());
}
;
/**
 * Get an auto generated copy of a filename
 *
 * @param filename
 * @return
 */
export function autoRename(filename) {
    let ext = getExtension(filename);
    let filenameNoExt;
    if (ext.length > 0)
        filenameNoExt = filename.substring(0, filename.length - ext.length - 1);
    else
        filenameNoExt = filename;
    let date = new Date();
    let newFilename = filenameNoExt + " (" + date.getHours().toString().padStart(2, "0")
        + date.getHours().toString().padStart(2, "0") + date.getMinutes().toString().padStart(2, "0")
        + date.getSeconds().toString().padStart(2, "0") + date.getMilliseconds().toString().padStart(3, "0") + ")";
    if (ext.length > 0)
        newFilename += "." + ext;
    return newFilename;
}
export function getExtension(fileName) {
    if (fileName == null)
        return "";
    let index = fileName.lastIndexOf(".");
    if (index >= 0) {
        return fileName.substring(index + 1);
    }
    else
        return "";
}
