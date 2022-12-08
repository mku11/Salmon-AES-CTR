package com.mku11.salmon.test;

import com.mku11.salmon.SalmonTime;

import java.io.File;

public class JavaTestHelper {

    public static String generateFolder(String dirPath) {
        long time = SalmonTime.currentTimeMillis();
        File dir = new File(dirPath + "_" + time);
        dir.mkdir();
        return dir.getPath();
    }
}
