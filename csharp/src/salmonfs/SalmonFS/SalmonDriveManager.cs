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
using System;

namespace Salmon.FS
{
    /// <summary>
    ///  Initializer class for setting the SalmonDrive implementation
    ///  Currently only 1 salmon drive instance is supported
    /// </summary>
    public class SalmonDriveManager
    {
        private static Type driveClassType;
        private static SalmonDrive drive;

        public static void SetVirtualDriveClass(Type driveClassType)
        {
            SalmonDriveManager.driveClassType = driveClassType;
            SetDriveLocation(null);
        }
        

        /// <summary>
        /// Get the current virtual drive.
        /// </summary>
        /// <returns></returns>
        public static SalmonDrive GetDrive()
        {
            return drive;
        }

        /// <summary>
        /// Set the vault location to a directory.
        /// This requires you previously use SetDriveClass() to provide a class for the drive
        /// </summary>
        /// <param name="dirPath">The directory path that will used for storing the contents of the vault</param>
        public static void SetDriveLocation(string dirPath)
        {
            drive = Activator.CreateInstance(driveClassType, new object[] { dirPath }) as SalmonDrive;
        }
    }
}
