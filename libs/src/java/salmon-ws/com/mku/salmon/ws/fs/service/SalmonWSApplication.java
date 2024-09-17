package com.mku.salmon.ws.fs.service;

import com.mku.salmon.ws.fs.service.security.SalmonAuthUsers;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Arrays;

/**
 * Simple REST API Web Service for file vaults. Note this is only for the real files, all encryption
 * and decryption happens at the client side. So make sure you choose an appropriate protocol
 * (most likely HTTPS) and your own authentication mechanism.
 */
@SpringBootApplication
public class SalmonWSApplication {

	private static ConfigurableApplicationContext ctx;

	public static void main(String[] args) throws Exception {
		if(args.length == 0 || args[0].equals("-h")) {
			printUsage();
			return;
		}
		if(args.length < 1) {
			throw new RuntimeException("No path to Salmon drive, please provide as argument");
		}

		FileSystem.setPath(args[0]);
		if(args.length == 1 || !args[1].equals("-np")){
			promptUserAndPasswd();
		}
		ctx = SpringApplication.run(SalmonWSApplication.class, args);
	}

	private static void promptUserAndPasswd() throws Exception {
		System.out.println("Setting up basic auth:");
		System.out.println("user:");
		String user = new String(System.console().readPassword());
		System.out.println("password:");
		String passswd = new String(System.console().readPassword());
		addUser(user, passswd);
	}

	private static void printUsage() {
		System.out.println("usage: java -cp salmon-ws-2.1.0.war org.springframework.boot.loader.WarLauncher /path/to/drive [-np]");
		System.out.println("-np: do not ask for password, use addUser() if you start from a Java application");
		System.out.println("-h: print usage");
	}

	public static void stop() {
		SpringApplication.exit(ctx);
	}

	public static void addUser(String user, String password) throws Exception {
		SalmonAuthUsers.addUser(user, password);
	}

	public static void removeUser(String user) throws Exception {
		SalmonAuthUsers.removeUser(user);
	}

	public static void removeAllUsers() {
		SalmonAuthUsers.removeAllUsers();
	}
}
