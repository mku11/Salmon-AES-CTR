package com.mku11.salmonfs ;
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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
  
 * Initializer class that will set the drive class and the root directory for the the salmon virtual drive
 * Currently it supports only 1 salmon drive
  
 */
public class SalmonDriveManager {
    private static Class driveClassType;
    private static SalmonDrive drive;

    public static void setVirtualDriveClass(Class driveClassType) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        SalmonDriveManager.driveClassType = driveClassType;
        setDriveLocation(null);
    }


    /**
     * Get the current virtual drive.
     
     */
    public static SalmonDrive getDrive() {
        return drive;
    }

    /**
     * Set the vault location to an external directory.
     * This requires you previously use SetDriveClass() to provide a class for the drive
     * @param dirPath The directory path that will used for storing the contents of the vault
     */
    public static void setDriveLocation(String dirPath) throws NoSuchMethodException, ClassNotFoundException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Class<?> clazz = Class.forName(driveClassType.getName());
        Constructor<?> ctor = clazz.getConstructor(String.class);
        drive = (SalmonDrive) ctor.newInstance(new Object[]{dirPath});
    }
}
