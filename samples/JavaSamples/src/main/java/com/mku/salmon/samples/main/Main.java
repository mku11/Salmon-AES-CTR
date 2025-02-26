package com.mku.salmon.samples.main;

import java.io.IOException;

public class Main
{
    public static void main(String[] args) throws Exception {
        TextProgram.main(args);
		DataProgram.main(args);
		DataStreamProgram.main(args);
		FileProgram.main(args);
		LocalDriveProgram.main(args);
		WebServiceDriveProgram.main(args);
		HttpDriveProgram.main(args);
    }
}