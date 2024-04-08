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

import { IRealFile } from "../file/ireal_file.js";
import { FileImporter } from "./file_importer.js";
import { FileExporter } from "./file_exporter.js";
import { SearchEvent, FileSearcher } from "./file_searcher.js";
import { IVirtualFile } from "../file/ivirtual_file.js";

/**
 * Facade class for file operations.
 */
export class FileCommander {
    protected fileImporter: FileImporter;
    protected fileExporter: FileExporter;
    protected fileSearcher: FileSearcher;
    private stopJobs: boolean = false;

    /**
     * Instantiate a new file commander object.
     *
     */
    public constructor(fileImporter: FileImporter, fileExporter: FileExporter, fileSearcher: FileSearcher) {
        this.fileImporter = fileImporter;
        this.fileExporter = fileExporter;
        this.fileSearcher = fileSearcher;
    }

    /**
     * Import files to the drive.
     *
     * @param filesToImport     The files to import.
     * @param importDir         The target directory.
     * @param deleteSource      True if you want to delete the source files when import complete.
     * @param integrity         True to apply integrity to imported files.
     * @param onProgressChanged Observer to notify when progress changes.
     * @param autoRename        Function to rename file if another file with the same filename exists
     * @param onFailed          Observer to notify when a file fails importing
     * @return The imported files if completes successfully.
     * @throws Exception
     */
    public async importFiles(filesToImport: IRealFile[], importDir: IVirtualFile,
        deleteSource: boolean, integrity: boolean,
        onProgressChanged: ((progress: RealFileTaskProgress) => void | null),
        autoRename: ((file: IRealFile) => Promise<string>) | null,
        onFailed: ((file: IRealFile, error: Error | unknown) => void | null)): Promise<IVirtualFile[]> {
        this.stopJobs = false;
        let importedFiles: IVirtualFile[] = [];

        let total: number = 0;
        for (let i = 0; i < filesToImport.length; i++)
            total += await this.getRealFilesCountRecursively(filesToImport[i]);
        let count: number[] = [0];
        let existingFiles: { [key: string]: IVirtualFile } = await this.getExistingIVirtualFiles(importDir);
        for (let i = 0; i < filesToImport.length; i++) {
            if (this.stopJobs)
                break;
            await this.importRecursively(filesToImport[i], importDir,
                deleteSource, integrity,
                onProgressChanged, autoRename, onFailed,
                importedFiles, count, total,
                existingFiles);
        }
        return importedFiles;
    }

    private async getExistingIVirtualFiles(importDir: IVirtualFile): Promise<{ [key: string]: IVirtualFile }> {
        let files: { [key: string]: IVirtualFile } = {};
        let realFiles: IVirtualFile[] = await importDir.listFiles();
        for (let i = 0; i < realFiles.length; i++) {
            let file: IVirtualFile = realFiles[i];
            try {
                files[await file.getBaseName()] = file;
            } catch (ignored) {
            }
        }
        return files;
    }

    private async importRecursively(fileToImport: IRealFile, importDir: IVirtualFile,
        deleteSource: boolean, integrity: boolean,
        onProgressChanged: ((progress: RealFileTaskProgress) => void) | null,
        autoRename: ((realFile: IRealFile) => Promise<string>) | null,
        onFailed: ((realFile: IRealFile, error: Error | unknown) => void) | null,
        importedFiles: IVirtualFile[], count: number[], total: number,
        existingFiles: { [key: string]: IVirtualFile }): Promise<void> {
        let sfile: IVirtualFile | null = fileToImport.getBaseName() in existingFiles
            ? existingFiles[fileToImport.getBaseName()] : null;
        if (await fileToImport.isDirectory()) {
            if (onProgressChanged != null)
                onProgressChanged(new RealFileTaskProgress(fileToImport, 0, 1, count[0], total));
            if (sfile == null || !await sfile.exists())
                sfile = await importDir.createDirectory(fileToImport.getBaseName());
            else if (sfile != null && await sfile.exists() && await sfile.isFile() && autoRename != null)
                sfile = await importDir.createDirectory(await autoRename(fileToImport));
            if (onProgressChanged != null)
                onProgressChanged(new RealFileTaskProgress(fileToImport, 1, 1, count[0], total));
            count[0]++;
            if (sfile == null)
                throw new Error("Could not get import directory");
            let nExistingFiles: { [key: string]: IVirtualFile } = await this.getExistingIVirtualFiles(sfile);
            let lFiles = await fileToImport.listFiles();
            for (let i = 0; i < lFiles.length; i++) {
                let child: IRealFile = lFiles[i];
                await this.importRecursively(child, sfile, deleteSource, integrity, onProgressChanged,
                    autoRename, onFailed, importedFiles, count, total,
                    nExistingFiles);
            }
            if (deleteSource)
                await fileToImport.delete();
        } else {
            try {
                let filename: string = fileToImport.getBaseName();
                if (sfile != null && (await sfile.exists() || await sfile.isDirectory()) && autoRename != null)
                    filename = await autoRename(fileToImport);
                sfile = await this.fileImporter.importFile(fileToImport, importDir, filename, deleteSource, integrity,
                    (bytes, totalBytes) => {
                        if (onProgressChanged != null) {
                            onProgressChanged(new RealFileTaskProgress(fileToImport,
                                bytes, totalBytes, count[0], total));
                        }
                    });
                if (sfile != null) {
                    existingFiles[await sfile.getBaseName()] = sfile;
                    importedFiles.push(sfile);
                }
                count[0]++;
            } catch (ex) {
                if(!this.onError(ex)){
                    if (onFailed != null)
                        onFailed(fileToImport, ex);
                }
            }
        }
    }

    /**
     * Export a file from a drive.
     *
     * @param filesToExport     The files to export.
     * @param exportDir         The export target directory
     * @param deleteSource      True if you want to delete the source files
     * @param integrity         True to use integrity verification before exporting files
     * @param onProgressChanged Observer to notify when progress changes.
     * @param autoRename        Function to rename file if another file with the same filename exists
     * @param onFailed          Observer to notify when a file fails exporting
     * @return The exported files
     * @throws Exception
     */
    public async exportFiles(filesToExport: IVirtualFile[], exportDir: IRealFile,
        deleteSource: boolean, integrity: boolean,
        onProgressChanged: ((progress: IVirtualFileTaskProgress) => void) | null,
        autoRename: ((realFile: IRealFile) => Promise<string>) | null,
        onFailed: ((file: IVirtualFile, error: Error | unknown) => void) | null): Promise<IRealFile[]> {
        this.stopJobs = false;
        let exportedFiles: IRealFile[] = [];

        let total: number = 0;
        for (let i = 0; i < filesToExport.length; i++)
            total += await this.getIVirtualFilesCountRecursively(filesToExport[i]);

        let existingFiles: { [key: string]: IRealFile } = await this.getExistingRealFiles(exportDir);

        let count: number[] = [0];
        for (let i = 0; i < filesToExport.length; i++) {
            if (this.stopJobs)
                break;
            await this.exportRecursively(filesToExport[i], exportDir,
                deleteSource, integrity,
                onProgressChanged, autoRename, onFailed,
                exportedFiles, count, total,
                existingFiles);
        }
        return exportedFiles;
    }

    private async getExistingRealFiles(exportDir: IRealFile): Promise<{ [key: string]: IRealFile }> {
        let files: { [key: string]: IRealFile } = {};
        let lFiles: IRealFile[] = await exportDir.listFiles();
        for (let i = 0; i < lFiles.length; i++) {
            let file: IRealFile = lFiles[i]
            files[file.getBaseName()] = file;
        }
        return files;
    }

    private async exportRecursively(fileToExport: IVirtualFile, exportDir: IRealFile,
        deleteSource: boolean, integrity: boolean,
        onProgressChanged: ((progress: IVirtualFileTaskProgress) => void) | null,
        autoRename: ((file: IRealFile) => Promise<string>) | null,
        onFailed: ((file: IVirtualFile, error: Error | unknown) => void) | null,
        exportedFiles: IRealFile[], count: number[], total: number,
        existingFiles: { [key: string]: IRealFile }): Promise<void> {
        let rfile: IRealFile | null = await fileToExport.getBaseName() in existingFiles
            ? existingFiles[await fileToExport.getBaseName()] : null;

        if (await fileToExport.isDirectory()) {
            if (rfile == null || !await rfile.exists())
                rfile = await exportDir.createDirectory(await fileToExport.getBaseName());
            else if (rfile != null && await rfile.isFile() && autoRename != null)
                rfile = await exportDir.createDirectory(await autoRename(rfile));
            if (onProgressChanged != null)
                onProgressChanged(new IVirtualFileTaskProgress(fileToExport as IVirtualFile, 1, 1, count[0], total));
            count[0]++;
            let nExistingFiles: { [key: string]: IRealFile } = await this.getExistingRealFiles(rfile);
            let lFiles: IVirtualFile[] = await fileToExport.listFiles();
            for (let i = 0; i < lFiles.length; i++) {
                let child: IVirtualFile = lFiles[i];
                await this.exportRecursively(child, rfile, deleteSource, integrity, onProgressChanged,
                    autoRename, onFailed, exportedFiles, count, total,
                    nExistingFiles);
            }
            if (deleteSource) {
                fileToExport.delete();
            }
        } else {
            try {
                let filename: string = await fileToExport.getBaseName();
                if (rfile != null && await rfile.exists() && autoRename != null)
                    filename = await autoRename(rfile);
                rfile = await this.fileExporter.exportFile(fileToExport as IVirtualFile, exportDir, filename, deleteSource, integrity,
                    (bytes, totalBytes) => {
                        if (onProgressChanged != null) {
                            onProgressChanged(new IVirtualFileTaskProgress(fileToExport as IVirtualFile,
                                bytes, totalBytes, count[0], total));
                        }
                    });
                if (rfile != null) {
                    existingFiles[rfile.getBaseName()] = rfile;
                    exportedFiles.push(rfile);
                }
                count[0]++;
            } catch (ex) {
                if(!this.onError(ex)){
                    if (onFailed != null)
                        onFailed(fileToExport as IVirtualFile, ex);
                }
            }
        }
    }

    private async getIVirtualFilesCountRecursively(file: IVirtualFile): Promise<number> {
        let count: number = 1;
        if (await file.isDirectory()) {
            let lFiles: IVirtualFile[] = await file.listFiles();
            for (let i = 0; i < lFiles.length; i++) {
                let child: IVirtualFile = lFiles[i];
                count += await this.getIVirtualFilesCountRecursively(child);
            }
        }
        return count;
    }

    private async getRealFilesCountRecursively(file: IRealFile): Promise<number> {
        let count: number = 1;
        if (await file.isDirectory()) {
            let lFiles: IRealFile[] = await file.listFiles();
            for (let i = 0; i < lFiles.length; i++) {
                let child: IRealFile = lFiles[i];
                count += await this.getRealFilesCountRecursively(child);
            }
        }
        return count;
    }

    /**
     * Delete files.
     *
     * @param filesToDelete         The files to delete.
     * @param OnProgressChanged The observer to notify when each file is deleted.
     * @param onFailed The observer to notify when a file has failed.
     */
    public async deleteFiles(filesToDelete: IVirtualFile[], OnProgressChanged: ((progress: IVirtualFileTaskProgress) => void) | null,
        onFailed: ((file: IVirtualFile, error: Error | unknown) => void) | null): Promise<void> {
        this.stopJobs = false;
        let count: number[] = [0];
        let total: number = 0;
        for (let i = 0; i < filesToDelete.length; i++)
            total += await this.getIVirtualFilesCountRecursively(filesToDelete[i]);
        for (let i = 0; i < filesToDelete.length; i++) {
            let fileToDelete: IVirtualFile = filesToDelete[i];
            if (this.stopJobs)
                break;
            let finalTotal: number = total;
            await fileToDelete.deleteRecursively((file, position, length) => {
                if (this.stopJobs)
                    throw new Error();
                if (OnProgressChanged != null) {
                    try {
                        OnProgressChanged(new IVirtualFileTaskProgress(
                            file, position, length, count[0], finalTotal));
                    }
                    catch (ex) {
                    }
                }
                if (position == length)
                    count[0]++;
            }, onFailed);
        }
    }

    /**
     * Copy files to another directory.
     *
     * @param filesToCopy       The array of files to copy.
     * @param dir               The target directory.
     * @param move              True if moving files instead of copying.
     * @param onProgressChanged The progress change observer to notify.
     * @param autoRename        The auto rename function to use when files with same filename are found
     * @param onFailed          The observer to notify when failures occur
     * @throws Exception
     */
    public async copyFiles(filesToCopy: IVirtualFile[], dir: IVirtualFile, move: boolean,
        onProgressChanged: ((progress: IVirtualFileTaskProgress) => void) | null,
        autoRename: ((file: IVirtualFile) => Promise<string>) | null,
        autoRenameFolders: boolean, onFailed: ((file: IVirtualFile, error: Error | unknown) => void) | null): Promise<void> {
        this.stopJobs = false;
        let count: number[] = [0];
        let total: number = 0;
        for (let i = 0; i < filesToCopy.length; i++)
            total += await this.getIVirtualFilesCountRecursively(filesToCopy[i]);
        const finalTotal: number = total;
        for (let i = 0; i < filesToCopy.length; i++) {
            let fileToCopy: IVirtualFile = filesToCopy[i];
            if (dir.getRealFile().getAbsolutePath().startsWith(fileToCopy.getRealFile().getAbsolutePath()))
                continue;

            if (this.stopJobs)
                break;

            if (move) {
                await fileToCopy.moveRecursively(dir, (file, position, length) => {
                    if (this.stopJobs)
                        throw new Error();
                    if (onProgressChanged != null) {
                        try {
                            onProgressChanged(new IVirtualFileTaskProgress(
                                file, position, length, count[0], finalTotal));
                        } catch (ex) {
                        }
                    }
                    if (position == length)
                        count[0]++;
                }, autoRename, autoRenameFolders, onFailed);
            } else {
                await fileToCopy.copyRecursively(dir, (file, position, length) => {
                    if (this.stopJobs)
                        throw new Error();
                    if (onProgressChanged != null) {
                        try {
                            onProgressChanged(new IVirtualFileTaskProgress(file, position, length, count[0], finalTotal));
                        } catch (ignored) {
                        }
                    }
                    if (position == length)
                        count[0]++;
                }, autoRename, autoRenameFolders, onFailed);
            }
        }
    }

    /**
     * Cancel all jobs.
     */
    public cancel(): void {
        this.stopJobs = true;
        this.fileImporter.stop();
        this.fileExporter.stop();
        this.fileSearcher.stop();
    }

    /**
     * True if the file search is currently running.
     *
     * @return
     */
    public isFileSearcherRunning(): boolean {
        return this.fileSearcher.isRunning();
    }

    /**
     * True if jobs are currently running.
     *
     * @return
     */
    public isRunning(): boolean {
        return this.fileSearcher.isRunning() || this.fileImporter.isRunning() || this.fileExporter.isRunning();
    }

    /**
     * True if file search stopped.
     *
     * @return
     */
    public isFileSearcherStopped(): boolean {
        return this.fileSearcher.isStopped();
    }

    /**
     * Stop file search.
     */
    public stopFileSearch() {
        this.fileSearcher.stop();
    }

    /**
     * Search
     *
     * @param dir           The directory to start the search.
     * @param terms         The terms to search for.
     * @param any           True if you want to match any term otherwise match all terms.
     * @param OnResultFound Callback interface to receive notifications when results found.
     * @param OnSearchEvent Callback interface to receive status events.
     * @return An array with all the results found.
     */
    public async search(dir: IVirtualFile, terms: string, anyTerm: boolean,
        OnResultFound: (searchResult: IVirtualFile) => void,
        OnSearchEvent: (event: SearchEvent) => void): Promise<IVirtualFile[]> {
        return await this.fileSearcher.search(dir, terms, anyTerm, OnResultFound, OnSearchEvent);
    }

    /**
     * True if all jobs are stopped.
     *
     * @return
     */
    public areJobsStopped(): boolean {
        return this.stopJobs;
    }

    /**
     * Get number of files recursively for the files provided.
     *
     * @param files Total number of files and files under subdirectories.
     * @return
     */
    private async getFiles(files: IVirtualFile[]): Promise<number> {
        let total = 0;
        for (let i = 0; i < files.length; i++) {
            let file: IVirtualFile = files[i];
            total++;
            if (await file.isDirectory()) {
                total += await this.getFiles(await file.listFiles());
            }
        }
        return total;
    }

    public close(): void {
        this.fileImporter.close();
        this.fileExporter.close();
    }

    /**
     * Rename an encrypted file
     *
     */
    public async renameFile(ifile: IVirtualFile, newFilename: string): Promise<void> {
        await ifile.rename(newFilename);
    }

    /**
     * Handle the error.
     * @param ex The error
     * @returns {boolean} True if handled
     */
    onError(ex: Error | unknown | null): boolean {
        return false;
    }
}


/**
 * File task progress class.
 */
export class FileTaskProgress {
    private readonly processedBytes: number;
    private readonly totalBytes: number;
    private readonly processedFiles: number;
    private readonly totalFiles: number;

    public getTotalBytes(): number {
        return this.totalBytes;
    }

    public getProcessedBytes(): number {
        return this.processedBytes;
    }

    public getProcessedFiles(): number {
        return this.processedFiles;
    }

    public getTotalFiles(): number {
        return this.totalFiles;
    }

    public constructor(processedBytes: number, totalBytes: number, processedFiles: number, totalFiles: number) {
        this.processedBytes = processedBytes;
        this.totalBytes = totalBytes;
        this.processedFiles = processedFiles;
        this.totalFiles = totalFiles;
    }
}

export class IVirtualFileTaskProgress extends FileTaskProgress {
    private readonly file: IVirtualFile;

    public getFile(): IVirtualFile {
        return this.file;
    }

    public constructor(file: IVirtualFile, processedBytes: number, totalBytes: number,
        processedFiles: number, totalFiles: number) {
        super(processedBytes, totalBytes, processedFiles, totalFiles);
        this.file = file;
    }
}

export class RealFileTaskProgress extends FileTaskProgress {
    public getFile(): IRealFile {
        return this.file;
    }

    private readonly file: IRealFile;

    public constructor(file: IRealFile, processedBytes: number, totalBytes: number,
        processedFiles: number, totalFiles: number) {
        super(processedBytes, totalBytes, processedFiles, totalFiles);
        this.file = file;
    }
}
