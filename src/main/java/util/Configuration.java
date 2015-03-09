package util;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

public final class Configuration {

//	public static final String KEYSTORE = Class.class.getResource("/keystore/key_server").getPath(); 
//	public static final String TRUSTSTORE = Class.class.getResource("/keystore/trust_client").getPath();
//	public static String KEY_SERVER_LIST = Class.class.getResource("/keyserver_list.txt").getPath();
	public static final String KEYSTORE = "keystore/key_server";
	public static final String TRUSTSTORE = "keystore/trust_client";
	public static String KEY_SERVER_LIST = "keyserver_list.txt";
	public static final String KEYSTORE_TYPE = "JCEKS";
	public static final String TRUSTSTORE_TYPE = "JCEKS";
	public static final String SERVER_SECRET_KEY = "server_key";

	public static String SECRET_KEY;
	public static String CLIENT_DIR;
	public static int CLIENT_PORT; 
	
	private Configuration() {
		// Do not instantiate
	}

	public static void initialize(String configFile) {
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(configFile));
		} catch (FileNotFoundException e) {
			System.err.println("System error: Configuration file not found.");
			e.printStackTrace();
		}
		if (reader != null) {
			try {
				String line = reader.readLine();
				while (line != null) {
					String[] pair = line.split(" ");
					switch (pair[0]) {
					case "clientDir" :
						CLIENT_DIR = pair[1];
						break;
					case "secretKey" :
						SECRET_KEY = pair[1];
						break;
					case "clientPort" :
						CLIENT_PORT = Integer.parseInt(pair[1]);
						break;
					}
					line = reader.readLine();
				}
			} catch (IOException e) {
				System.err.println("System error: Error reading configuration file.");
				e.printStackTrace();
			}
			finally {
				try {
					reader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
}
