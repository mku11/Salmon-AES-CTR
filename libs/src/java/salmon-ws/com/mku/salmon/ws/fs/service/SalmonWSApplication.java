package com.mku.salmon.ws.fs.service;

import com.mku.file.JavaFile;
import com.mku.salmon.SalmonDrive;
import com.mku.salmon.drive.JavaDrive;
import com.mku.sequence.INonceSequencer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.util.Arrays;

@SpringBootApplication
public class SalmonWSApplication {
	private static String path;

	public static SalmonDrive getDrive() {
		return drive;
	}

	public static void main(String[] args) throws IOException {
		if(args.length != 1) {
			throw new RuntimeException("No path to Salmon drive, please provide as argument");
		}

		SpringApplication.run(SalmonWSApplication.class, args);
	}
}
