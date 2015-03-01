package util;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

public final class Configuration {

	public static String KEYSTORE; 
	public static String TRUSTSTORE; 
	public static String KEYSTORE_TYPE;
	public static String TRUSTSTORE_TYPE;
	public static String CLIENT_DIR;
	public static String SECRET_KEY;
	
	public static int CLIENT_PORT; 
	public static int MAX_CONNECTIONS; 
	
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
					case "keyStore" :
						KEYSTORE = pair[1];
						break;
					case "trustStore" :
						TRUSTSTORE = pair[1];
						break;
					case "keyStoreType" : 
						KEYSTORE_TYPE = pair[1];
						break;
					case "trustStoreType" : 
						TRUSTSTORE_TYPE = pair[1];
						break;
					case "clientDir" :
						CLIENT_DIR = pair[1];
						break;
					case "secretKey" :
						SECRET_KEY = pair[1];
						break;
					case "clientPort" :
						CLIENT_PORT = Integer.parseInt(pair[1]);
						break;
					case "maxConnections" :
						MAX_CONNECTIONS = Integer.parseInt(pair[1]);
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
