/*
MIT License

Copyright (c) 2025 Max Kas

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

import { RandomAccessStream } from '../../salmon-core/streams/random_access_stream.js';
import { Base64 } from '../../salmon-core/convert/base64.js';
import { IRealFile } from './ireal_file.js';
import { JsWSFileStream } from '../streams/js_ws_file_stream.js';
import { IOException } from '../../salmon-core/streams/io_exception.js';

/**
 * Salmon RealFile implementation for Web Service files.
 */
export class JsWSFile implements IRealFile {
    static readonly #PATH: string = "path";
    static readonly #DEST_DIR: string = "destDir";
    static readonly #FILENAME: string = "filename";
    public static readonly separator: string = "/";
    public static readonly SMALL_FILE_MAX_LENGTH: number = 1 * 1024 * 1024;

    #filePath: string;
    #servicePath: string;
	response: any | null;

    public getServicePath(): string | null{
        return this.#servicePath;
    }

    public getCredentials(): Credentials | null {
        return this.#credentials;
    }

    #credentials: Credentials;

    public setCredentials(credentials: Credentials) {
        this.#credentials = credentials;
    }

    /**
     * Instantiate a real file represented by the filepath provided.
     * @param path The filepath.
     */
    public constructor(path: string, servicePath: string, credentials: Credentials) {
        this.#servicePath = servicePath;
        if(!path.startsWith(JsWSFile.separator))
            path = JsWSFile.separator + path;
        this.#filePath = path;
        this.#credentials = credentials;
    }

    async getResponse(): Promise<any> {
		if (this.response == null) {
			let headers = new Headers();
			this.setDefaultHeaders(headers);
			this.setServiceAuth(headers);
			let httpResponse: Response | null = null;
			httpResponse = (await fetch(this.#servicePath + "/api/info" 
				+ "?" + JsWSFile.#PATH + "=" + encodeURIComponent(this.getPath()),
				{method: 'GET', keepalive: true, headers: headers}));
			await this.#checkStatus(httpResponse, 200);
			this.response = await httpResponse.json();
		}
        return this.response;
    }

    /**
     * Create a directory under this directory.
     * @param dirName The name of the new directory.
     * @return The newly created directory.
     */
    public async createDirectory(dirName: string): Promise<IRealFile> {
        let nDirPath: string = this.getChildPath(dirName);
        let headers: Headers = new Headers();
        this.setDefaultHeaders(headers);
        this.setServiceAuth(headers);
        let params: URLSearchParams = new URLSearchParams();
        params.append(JsWSFile.#PATH, nDirPath);
        let httpResponse: Response | null = null;
        httpResponse = (await fetch(this.#servicePath + "/api/mkdir", 
            { method: 'POST', keepalive: true, body: params, headers: headers }));
        await this.#checkStatus(httpResponse, 200);
        let dir: JsWSFile = new JsWSFile(nDirPath, this.#servicePath, this.#credentials);
        return dir;
    }

    /**
     * Create a file under this directory.
     * @param filename The name of the new file.
     * @return The newly created file.
     * @throws IOException Thrown if there is an IO error.
     */
    public async createFile(filename: string): Promise<IRealFile> {
        let nFilePath: string = this.getChildPath(filename);
        let headers: Headers = new Headers();
        this.setDefaultHeaders(headers);
        this.setServiceAuth(headers);
        let params: URLSearchParams = new URLSearchParams();
        params.append(JsWSFile.#PATH, nFilePath);
        let httpResponse: Response | null = null;
        httpResponse = (await fetch(this.#servicePath + "/api/create", 
            { method: 'POST', keepalive: true, body: params, headers: headers }));
        await this.#checkStatus(httpResponse, 200);
        let nFile: JsWSFile = new JsWSFile(nFilePath, this.#servicePath, this.#credentials);
        return nFile;
    }

    /**
     * Delete this file or directory.
     * @return True if deletion is successful.
     */
    public async delete(): Promise<boolean> {
		this.reset();
        if(await this.isDirectory()) {
            let files: IRealFile[]  = await this.listFiles();
            for (let file of files) {
                let headers: Headers = new Headers();
                this.setDefaultHeaders(headers);
                this.setServiceAuth(headers);
                let params: URLSearchParams = new URLSearchParams();
                params.append(JsWSFile.#PATH, file.getPath());
                let httpResponse: Response | null = null;
                httpResponse = (await fetch(this.#servicePath + "/api/delete", 
                    { method: 'DELETE', keepalive: true, body: params, headers: headers }));
                await this.#checkStatus(httpResponse, 200);
            }
        }

        let headers: Headers = new Headers();
        this.setDefaultHeaders(headers);
        this.setServiceAuth(headers);
        let params: URLSearchParams = new URLSearchParams();
        params.append(JsWSFile.#PATH, this.#filePath);
        let httpResponse: Response | null = null;
        httpResponse = (await fetch(this.#servicePath + "/api/delete", 
            { method: 'DELETE', keepalive: true, body: params, headers: headers }));
        await this.#checkStatus(httpResponse, 200);
		this.reset();
        return true;
    }

    /**
     * True if file or directory exists.
     * @return
     */
    public async exists(): Promise<boolean> {
        return (await this.getResponse()).present;
    }

    /**
     * Get the absolute path on the physical disk. For javascript this is the same as the filepath.
     * @return The absolute path.
     */
    public getAbsolutePath(): string {
        return this.#filePath;
    }

    /**
     * Get the name of this file or directory.
     * @return The name of this file or directory.
     */
    public getBaseName(): string {
        if (this.#filePath == null)
            throw new Error("Filepath is not assigned");
        let nFilePath = this.#filePath;
        if(nFilePath.endsWith("/"))
            nFilePath = nFilePath.substring(0,nFilePath.length-1);
        let basename: string | undefined = nFilePath.split(JsWSFile.separator).pop();
        if (basename === undefined)
            throw new Error("Could not get basename");
        if (basename.includes("%")){
            basename = decodeURIComponent(basename);
        }
        return basename;
    }

    /**
     * Get a stream for reading the file.
     * @return The stream to read from.
     * @throws FileNotFoundException
     */
    public async getInputStream(): Promise<RandomAccessStream> {
		this.reset();
        let fileStream: JsWSFileStream = new JsWSFileStream(this, "r");
        return fileStream;
    }

    /**
     * Get a stream for writing to this file.
     * @return The stream to write to.
     * @throws FileNotFoundException
     */
    public async getOutputStream(): Promise<RandomAccessStream> {
		this.reset();
        let fileStream: JsWSFileStream = new JsWSFileStream(this, "rw");
        return fileStream;
    }

    /**
     * Get the parent directory of this file or directory.
     * @return The parent directory.
     */
    public async getParent(): Promise<IRealFile> {
		let path: string = this.#filePath;
		if(path.endsWith(JsWSFile.separator))
			path = path.slice(0,-1);
        let parentFilePath: string = path.substring(0, path.lastIndexOf(JsWSFile.separator));
        return new JsWSFile(parentFilePath, this.#servicePath,this.#credentials);
    }

    /**
     * Get the path of this file. For javascript this is the same as the absolute filepath.
     * @return
     */
    public getPath(): string {
        return this.#filePath;
    }

    /**
     * True if this is a directory.
     * @return
     */
    public async isDirectory(): Promise<boolean> {
        return (await this.getResponse()).directory;
    }

    /**
     * True if this is a file.
     * @return
     */
    public async isFile(): Promise<boolean> {
        return !await this.isDirectory();
    }

    /**
     * Get the last modified date on disk.
     * @return
     */
    public async lastModified(): Promise<number> {
        return (await this.getResponse()).lastModified;
    }

    /**
     * Get the size of the file on disk.
     * @return
     */
    public async length(): Promise<number> {
        return (await this.getResponse()).length;
    }

    /**
     * Get the count of files and subdirectories
     * @return
     */
    public async getChildrenCount(): Promise<number> {
        if(await this.isDirectory()) {
            let headers: Headers = new Headers();
            this.setDefaultHeaders(headers);
            this.setServiceAuth(headers);
            let httpResponse: Response | null = null;
            httpResponse = (await fetch(this.#servicePath + "/api/list"
                + "?" + JsWSFile.#PATH + "=" + encodeURIComponent(this.getPath()), 
                { method: 'GET', keepalive: true, headers: headers }));
            await this.#checkStatus(httpResponse, 200);
            let res: number = (await httpResponse.json()).length;
            return res;
        }
        return 0;
    }

    /**
     * List all files under this directory.
     * @return The list of files.
     */
    public async listFiles(): Promise<IRealFile[]> {
        if(await this.isDirectory()) {
            let headers: Headers = new Headers();
            this.setDefaultHeaders(headers);
            this.setServiceAuth(headers);
            let httpResponse: Response | null = null;
            httpResponse = (await fetch(this.#servicePath + "/api/list"
                + "?" + JsWSFile.#PATH + "=" + encodeURIComponent(this.getPath()),
                { method: 'GET', keepalive: true, headers: headers }));
            await this.#checkStatus(httpResponse, 200);
            let realFiles: JsWSFile[] = [];
            let realDirs: JsWSFile[] = [];
            for(let resFile of await httpResponse.json()){
                let file: JsWSFile = new JsWSFile(resFile.path, this.#servicePath, this.#credentials);
                if (resFile.directory)
                    realDirs.push(file);
                else
                    realFiles.push(file);
            }
            realDirs.push(...realFiles);
            return realDirs;
        }
        return [];
    }

    /**
     * Move this file or directory under a new directory.
     * @param newDir The target directory.
     * @param newName The new filename
     * @param progressListener Observer to notify when progress changes.
     * @return The moved file. Use this file for subsequent operations instead of the original.
     */
    public async move(newDir: IRealFile, newName: string | null = null, progressListener: ((position: number, length: number) => void) | null = null): Promise<IRealFile> {
        newName = newName != null ? newName : this.getBaseName();
        if (newDir == null || !newDir.exists())
            throw new IOException("Target directory does not exist");
        let newFile: IRealFile | null = await newDir.getChild(newName);
        if (newFile != null && await newFile.exists())
            throw new IOException("Another file/directory already exists");

        if (await this.isDirectory()) {
            throw new IOException("Could not move directory use IRealFile moveRecursively() instead");
        } else {
            let headers: Headers = new Headers();
            this.setDefaultHeaders(headers);
            this.setServiceAuth(headers);
            let params: URLSearchParams = new URLSearchParams();
            params.append(JsWSFile.#PATH, this.#filePath);
            params.append(JsWSFile.#DEST_DIR, newDir.getPath());
            params.append(JsWSFile.#FILENAME, newName);
            let httpResponse: Response | null = null;
            httpResponse = (await fetch(this.#servicePath + "/api/move", 
                { method: 'PUT', keepalive: true, body: params, headers: headers }));
            await this.#checkStatus(httpResponse, 200);
            newFile = new JsWSFile((await httpResponse.json()).path, this.#servicePath, this.#credentials);
			this.reset();
            return newFile;
        }
    }

    /**
     * Move this file or directory under a new directory.
     * @param newDir    The target directory.
     * @param newName   New filename
     * @param progressListener Observer to notify when progress changes.
     * @return The copied file. Use this file for subsequent operations instead of the original.
     * @throws IOException Thrown if there is an IO error.
     */
    public async copy(newDir: IRealFile, newName: string | null = null, progressListener: ((position: number, length: number) => void) | null = null): Promise<IRealFile> {
        newName = newName != null ? newName : this.getBaseName();
        if (newDir == null || !newDir.exists())
            throw new IOException("Target directory does not exists");
        let newFile: IRealFile | null = await newDir.getChild(newName);
        if (newFile != null && await newFile.exists())
            throw new IOException("Another file/directory already exists");
        if (await this.isDirectory()) {
            throw new IOException("Could not copy directory use IRealFile copyRecursively() instead");
        } else {
            let headers: Headers = new Headers();
            this.setDefaultHeaders(headers);
            this.setServiceAuth(headers);
            let params: URLSearchParams = new URLSearchParams();
            params.append(JsWSFile.#PATH, this.#filePath);
            params.append(JsWSFile.#DEST_DIR, newDir.getPath());
            params.append(JsWSFile.#FILENAME, newName);
            let httpResponse: Response | null = null;
            httpResponse = (await fetch(this.#servicePath + "/api/copy", 
                { method: 'POST', keepalive: true, body: params, headers: headers }));
            await this.#checkStatus(httpResponse, 200);
            newFile = new JsWSFile((await httpResponse.json()).path, this.#servicePath, this.#credentials);
			this.reset();
            return newFile;
        }
    }

    /**
     * Get the file or directory under this directory with the provided name.
     * @param filename The name of the file or directory.
     * @return
     */
    public async getChild(filename: string): Promise<IRealFile | null> {
        if (await this.isFile())
            return null;
        let nFilepath: string = this.getChildPath(filename);
        let child: JsWSFile = new JsWSFile(nFilepath, this.#servicePath, this.#credentials);
        return child;
    }

    /**
     * Rename the current file or directory.
     * @param newFilename The new name for the file or directory.
     * @return True if successfully renamed.
     */
    public async renameTo(newFilename: string): Promise<boolean> {
		this.reset();
        let headers: Headers = new Headers();
        this.setDefaultHeaders(headers);
        this.setServiceAuth(headers);
        let params: URLSearchParams = new URLSearchParams();
        params.append(JsWSFile.#PATH, this.#filePath);
        params.append(JsWSFile.#FILENAME, newFilename);
        let httpResponse: Response | null = null;
        httpResponse = (await fetch(this.#servicePath + "/api/rename", 
            { method: 'PUT', keepalive: true, body: params, headers: headers }));
        await this.#checkStatus(httpResponse, 200);
        return true;
    }

    /**
     * Create this directory under the current filepath.
     * @return True if created.
     */
    public async mkdir(): Promise<boolean> {
		this.reset();
        let headers: Headers = new Headers();
        this.setDefaultHeaders(headers);
        this.setServiceAuth(headers);
        let params: URLSearchParams = new URLSearchParams();
        params.append(JsWSFile.#PATH, this.#filePath);
        let httpResponse: Response | null = null;
        httpResponse = (await fetch(this.#servicePath + "/api/mkdir", 
            { method: 'POST', keepalive: true, body: params, headers: headers }));
        await this.#checkStatus(httpResponse, 200);
        return true;
    }
	
	/**
     * Reset cached properties
     */
    public reset() {
		this.response = null;
	}

    private getChildPath(filename: string) {
        let nFilepath = this.#filePath;
        if(!nFilepath.endsWith(JsWSFile.separator))
            nFilepath += JsWSFile.separator;
        nFilepath += filename;
        return nFilepath;
    }
	
    /**
     * Returns a string representation of this object
     */
    public toString(): string {
        return this.#filePath;
    }

    private setServiceAuth(headers: Headers) {
        if(!this.#credentials)
            return;
        headers.append('Authorization', 'Basic ' + new Base64().encode(
            new TextEncoder().encode(this.#credentials.getServiceUser() + ":" + this.#credentials.getServicePassword())));
    }
    
    async #checkStatus(httpResponse: Response, status: number) {
        if (httpResponse.status != status)
            throw new IOException(httpResponse.status
                    + " " + httpResponse.statusText + "\n"
                    + await httpResponse.text());
    }

    private setDefaultHeaders(headers: Headers) {
        headers.append("Cache", "no-store");
		headers.append("Connection", "keep-alive");
    }
}

export class Credentials {
    readonly #serviceUser: string;

    public getServiceUser(): string {
        return this.#serviceUser;
    }

    public getServicePassword(): string {
        return this.#servicePassword;
    }

    readonly #servicePassword: string;

    public constructor(serviceUser: string, servicePassword: string) {
        this.#serviceUser = serviceUser;
        this.#servicePassword = servicePassword;
    }
}
