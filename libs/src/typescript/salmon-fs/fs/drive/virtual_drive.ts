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

import { IRealFile } from "../file/ifile";
import { IVirtualFile } from "../file/ivirtual_file";

/*
 * Virtual Drive 
 */
export abstract class VirtualDrive {
    /**
     * Method is called when the user is authenticated
     */
    public abstract onUnlockSuccess(): void;

    /**
     * Method is called when unlocking the drive has failed
     */
    public abstract onUnlockError(): void;
	
    /**
     * Get a private dir for sharing files with other apps.
     */
	public abstract getPrivateDir(): IRealFile;

    /**
     * Get a virtual file backed by a real file.
     * @param file The real file
     */
    public abstract getVirtualFile(file: IRealFile): IVirtualFile;
	
	/**
     * Return the virtual root directory of the drive.
     * @return
     * @throws SalmonAuthException Thrown when error during authorization
     */
	public abstract getRoot(): Promise<IVirtualFile | null>;
}
