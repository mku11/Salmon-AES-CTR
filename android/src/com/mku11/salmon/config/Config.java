package com.mku11.salmon.config;

import com.mku11.salmon.SalmonGenerator;

public class Config {
    public static final boolean enableNativeLib = false;
    public static final SalmonGenerator.PbkdfAlgo pbkdfAlgo = SalmonGenerator.PbkdfAlgo.SHA256;
    public static boolean allowScreenContents = false;
}