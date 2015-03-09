package services;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;

import util.Configuration;
import util.ConsoleHelper;
import util.Util;
import network.TorNetwork;

public final class PgpKeyService {
	private final String GET_URL = "/pks/lookup?op=get&search=";
	private final Pattern KEY_PATTERN = 
			Pattern.compile("(-----BEGIN PGP PUBLIC KEY BLOCK-----.*-----END PGP PUBLIC KEY BLOCK-----)", Pattern.DOTALL);
	private final TorNetwork network;
	private String[] keyServers;
	
	public PgpKeyService(TorNetwork network) {
		this.network = network;
		Set<String> servers = parseKeyServers();
		keyServers = new String[servers.size()];
		servers.toArray(keyServers);
	}
	
	public PGPPublicKey retrieveKey(String username) {
		HttpClient httpClient = this.network.getHttpClient();
		try {
			HttpResponse response = httpClient.execute(
					new HttpHost(getHostName(), -1, "https"), new HttpGet(GET_URL + username));
			if (response.getStatusLine().getStatusCode() == 200) {
				String responseString = EntityUtils.toString(response.getEntity());
				Matcher matcher = KEY_PATTERN.matcher(responseString);
				if (matcher.find()) {
					return Util.getPGPPublicKey(matcher.group());
				}
			}
		} catch (IOException | ParseException | PGPException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public String getHostName() {
		String host = keyServers[Math.abs(Util.getRandomInt()) % keyServers.length];
		ConsoleHelper.printBlue("Using key server: " + host);
		return host;
	}
	
	private Set<String> parseKeyServers() {
		Set<String> serverList = new HashSet<String>();
		File f = new File(Configuration.KEY_SERVER_LIST);
		Scanner s;
		try {
			s = new Scanner(new FileInputStream(f));
			while(s.hasNext()) {
				serverList.add(s.nextLine());
			}
			s.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		return serverList;
	}
//	public static void main(String[] args) throws FileNotFoundException {
//		Configuration.initialize("server_config");
//		System.setProperty("javax.net.ssl.trustStore", Configuration.TRUSTSTORE);
//		System.setProperty("javax.net.ssl.trustStoreType", Configuration.TRUSTSTORE_TYPE);
//		System.setProperty("javax.net.ssl.trustStorePassword", "client");
//		
//		TorNetwork torNetwork = new TorNetwork("127.0.0.1", 9050);
//		PgpKeyService keyService = new PgpKeyService(torNetwork);
//		File f = new File(Configuration.KEY_SERVER_LIST);
//		Scanner s = new Scanner(new FileInputStream(f));
//		while (s.hasNext()) {
//			String line = s.nextLine();
//			keyService.keyServers = new String[] {line};
//			try {
//				System.out.println("...");
//				if (keyService.retrieveKey("jlzhao") == null) {
//					System.out.println("Failed: " + line);
//				}
//			} catch (Exception e) {
//				System.out.println("Failed: " + line);
//			}
//		}
//		System.out.println("Done");
//	}
}
