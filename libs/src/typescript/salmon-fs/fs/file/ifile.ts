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

import { RandomAccessStream } from "../../../salmon-core/streams/random_access_stream.js";

/**
 * Interface that represents a real file. This class is used internally by the virtual disk to
 * import, store, and export the encrypted files.
 * Extend this to provide an interface to any file system, platform, or API ie: on disk, memory, network, or cloud.
 */
export interface IFile {
    /**
     * True if this file exists.
     *
     * @return {Promise<boolean>}
     */
    exists(): Promise<boolean>;

    /**
     * Delete this file.
     *
     * @return {Promise<boolean>}
     */
    delete(): Promise<boolean>;

    /**
     * Get a stream for reading the file.
     *
     * @return
     * @throws {Promise<RandomAccessStream>} FileNotFoundException
     */
    getInputStream(): Promise<RandomAccessStream>;

    /**
     * Get a stream for writing to the file.
     *
     * @return
     * @throws {Promise<RandomAccessStream>} FileNotFoundException
     */
    getOutputStream(): Promise<RandomAccessStream>;

    /**
     * Rename file.
     *
     * @param newFilename The new filename
     * @return {Promise<boolean>} True if success.
     * @throws FileNotFoundException
     */
    renameTo(newFilename: string): Promise<boolean>;

    /**
     * Get the length for the file.
     *
     * @return {Promise<number>} The length.
     */
    getLength(): Promise<number>;

    /**
     * Get the count of files and subdirectories
     *
     * @return {Promise<number>}
     */
    getChildrenCount(): Promise<number>;

    /**
     * Get the last modified date of the file.
     *
     * @return {Promise<number>}
     */
    getLastDateModified(): Promise<number>;

    /**
     * Get the absolute path or handle of the file on disk.
     *
     * @return {string}
     */
    getDisplayPath(): string;

    /**
     * Get the original filepath of this file. This might represent a symlinks or merged folders or a FileHandle. To get the display path
     * use {@link #getADisplayPath()}.
     *
     * @return
     */
    getPath(): any;

    /**
     * True if this is a file.
     *
     * @return {Promise<boolean>}
     */
    isFile(): Promise<boolean>;

    /**
     * True if this is a directory.
     *
     * @return {Promise<boolean>}
     */
    isDirectory(): Promise<boolean>;

    /**
     * Get all files and directories under this directory.
     *
     * @return {Promise<IFile[]>}
     */
    listFiles(): Promise<IFile[]>;

    /**
     * Get the basename of the file.
     *
     * @return {string}
     */
    getName(): string;

    /**
     * Create the directory with the name provided under this directory.
     *
     * @param {string} dirName Directory name.
     * @return {Promise<IFile>} The newly created directory.
     */
    createDirectory(dirName: string): Promise<IFile>;

    /**
     * Get the parent directory of this file/directory.
     *
     * @return {Promise<IFile | null>} The parent directory.
     */
    getParent(): Promise<IFile | null>;

    /**
     * Create an empty file with the provided name.
     *
     * @param {string} filename The name for the new file.
     * @return {Promise<IFile>} The newly create file.
     * @throws IOException Thrown if there is an IO error.
     */
    createFile(filename: string): Promise<IFile>;

    /**
     * Move this file to another directory.
     *
     * @param {IFile} newDir           The target directory.
     * @param {string | null} newName          The new filename.
     * @param {((position: number, length: number) => void) | null} progressListener Observer to notify of the move progress.
     * @return {Promise<IFile>} The file after the move. Use this instance for any subsequent file operations.
     */
    move(newDir: IFile, newName: string | null, progressListener: ((position: number, length: number) => void) | null): Promise<IFile>;

    /**
     * Copy this file to another directory.
     *
     * @param {IFile} newDir           The target directory.
     * @param {string | null} newName          The new filename.
     * @param {((position: number, length: number) => void) | null} progressListener Observer to notify of the copy progress.
     * @return {Promise<IFile | null>} The file after the copy. Use this instance for any subsequent file operations.
     * @throws IOException Thrown if there is an IO error.
     */
    copy(newDir: IFile, newName: string | null, progressListener: ((position: number, length: number) => void) | null): Promise<IFile | null>;

    /**
     * Get the file/directory matching the name provided under this directory.
     *
     * @param {string} filename The name of the file or directory to match.
     * @return {Promise<IFile | null>} The file that was matched.
     */
    getChild(filename: string): Promise<IFile | null>;

    /**
     * Create a directory with the current filepath.
     *
     * @return {Promise<boolean>}
     */
    mkdir(): Promise<boolean>;
	
	/**
     * Reset cached properties
     */
    reset(): any;

}

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
export async function copyFileContents(src: IFile, dest: IFile, deleteAfter: boolean,
    progressListener: ((position: number, length: number) => void) | null): Promise<boolean> {
    let source: RandomAccessStream = await src.getInputStream();
    let target: RandomAccessStream = await dest.getOutputStream();
    try {
        await source.copyTo(target, 0, progressListener);
    } catch (ex) {
        await dest.delete();
        return false;
    } finally {
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
 * @param {IFile} src Source directory
 * @param {IFile} destDir Destination directory to copy into.
 * @param {((realfile: IFile) => Promise<string>) | null} autoRename Autorename callback (default is none).
 * @param {boolean} autoRenameFolders Apply autorename to folders also (default is false)
 * @param {((realfile: IFile, ex: Error) => void) | null} onFailed OnFailed callback
 * @param {((realfile: IFile, position: number, length: number) => void) | null} progressListener Progress listener
 * @throws IOException Thrown if there is an IO error.
 */
export async function copyRecursively(src: IFile, destDir: IFile,
    autoRename: ((realfile: IFile) => Promise<string>) | null = null,
    autoRenameFolders: boolean = false,
    onFailed: ((realfile: IFile, ex: Error) => void) | null = null,
    progressListener: ((realfile: IFile, position: number, length: number) => void) | null = null): Promise<void> {
    let newFilename: string = src.getName();
    let newFile: IFile | null;
    newFile = await destDir.getChild(newFilename);
    if (await src.isFile()) {
        if (newFile != null && await newFile.exists()) {
            if (autoRename != null) {
                newFilename = await autoRename(src);
            } else {
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
    } else if (await src.isDirectory()) {
        if (progressListener != null)
            progressListener(src, 0, 1);
        if (destDir.getDisplayPath().startsWith(src.getDisplayPath())) {
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
            await copyRecursively(child, newFile, autoRename, autoRenameFolders, onFailed, progressListener);
        }
    }
}

/**
 * Move a directory recursively
 *
 * @param {IFile} src Source directory
 * @param {IFile} destDir Destination directory to move into.
 * @param {((realfile: IFile) => Promise<string>) | null} autoRename Autorename callback (default is none).
 * @param {boolean} autoRenameFolders Apply autorename to folders also (default is false)
 * @param {((realfile: IFile, ex: Error) => void) | null} onFailed OnFailed callback
 * @param {((realfile: IFile, position: number, length: number) => void) | null} progressListener Progress listener
 */
export async function moveRecursively(file: IFile, destDir: IFile,
    autoRename: ((realFile: IFile) => Promise<string>) | null = null,
    autoRenameFolders: boolean = false,
    onFailed: ((realFile: IFile, ex: Error) => void) | null = null,
    progressListener: ((realFile: IFile, position: number, length: number) => void) | null = null,): Promise<void> {
    // target directory is the same
    let parent: IFile | null = await file.getParent();
    if (parent != null && parent.getDisplayPath() == destDir.getDisplayPath()) {
        if (progressListener != null) {
            progressListener(file, 0, 1);
            progressListener(file, 1, 1);
        }
        return;
    }

    let newFilename: string = file.getName();
    let newFile: IFile | null;
    newFile = await destDir.getChild(newFilename);
    if (await file.isFile()) {
        if (newFile != null && await newFile.exists()) {
            if (newFile.getDisplayPath() == file.getDisplayPath())
                return;
            if (autoRename != null) {
                newFilename = await autoRename(file);
            } else {
                if (onFailed != null)
                    onFailed(file, new Error("Another file exists"));
                return;
            }
        }
        await file.move(destDir, newFilename, (position: number, length: number) => {
            if (progressListener != null) {
                progressListener(file, position, length);
            }
        });
    } else if (await file.isDirectory()) {
        if (progressListener != null)
            progressListener(file, 0, 1);
        if (destDir.getDisplayPath().startsWith(file.getDisplayPath())) {
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
            await moveRecursively(child, newFile, autoRename, autoRenameFolders, onFailed, progressListener);
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
 * @param onFailed Callback when delete fails
 * @param progressListener The progress listener
 */
export async function deleteRecursively(file: IFile, 
    onFailed: ((realFile: IFile, ex: Error) => void) | null,
    progressListener: (realfile: IFile, position: number, length: number) => void): Promise<void> {
    if (await file.isFile()) {
        progressListener(file, 0, 1);
        if (!file.delete()) {
            if (onFailed != null)
                onFailed(file, new Error("Could not delete file"));
            return;
        }
        progressListener(file, 1, 1);
    } else if (await file.isDirectory()) {
        for (let child of await file.listFiles()) {
            await deleteRecursively(child, onFailed, progressListener);
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
export async function autoRenameFile(file: IFile) {
    return autoRename(file.getName());
};

/**
 * Get an auto generated copy of a filename
 *
 * @param filename
 * @return
 */
export function autoRename(filename: string): string {
    let ext: string = getExtension(filename);
    let filenameNoExt: string;
    if (ext.length > 0)
        filenameNoExt = filename.substring(0, filename.length - ext.length - 1);
    else
        filenameNoExt = filename;
    let date: Date = new Date();
    let newFilename: string = filenameNoExt + " (" + date.getHours().toString().padStart(2, "0")
        + date.getHours().toString().padStart(2, "0") + date.getMinutes().toString().padStart(2, "0")
        + date.getSeconds().toString().padStart(2, "0") + date.getMilliseconds().toString().padStart(3, "0") + ")";
    if (ext.length > 0)
        newFilename += "." + ext;
    return newFilename;
}

export function getExtension(fileName: string): string {
    if (fileName == null)
        return "";
    let index: number = fileName.lastIndexOf(".");
    if (index >= 0) {
        return fileName.substring(index + 1);
    } else
        return "";
}
