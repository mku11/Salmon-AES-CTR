package com.mku.salmon.ws.fs.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


import java.util.Arrays;

/**
 * Simple REST API Web Service for file vaults. Note this is only for the real files, all encryption
 * and decryption happens at the client side. So make sure you choose an appropriate protocol
 * (most likely HTTPS) and your own authentication mechanism.
 */
@SpringBootApplication
public class SalmonWSApplication {

	public static void main(String[] args) {
		if(args.length != 1) {
			throw new RuntimeException("No path to Salmon drive, please provide as argument");
		}
		FileSystem.setPath(args[0]);
		SpringApplication.run(SalmonWSApplication.class, args);
	}
}
