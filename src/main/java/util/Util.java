package util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public final class Util {

	public static char[] readPassword() {
		// Workaround for running in Eclipse, not completely safe
		if (System.console() == null) {
			BufferedReader bf = new BufferedReader(new InputStreamReader(System.in));
			try {
				char[] pass = bf.readLine().toCharArray();
				return pass;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		System.out.println("Enter server password: ");
		return System.console().readPassword();
	}
}
