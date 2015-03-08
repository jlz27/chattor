package services;

import java.io.IOException;
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

import util.Util;
import network.TorNetwork;

public final class PgpService {
	private final String HOSTNAME = "pgp.mit.edu";
	private final String HOSTNAME2 = "hkps.pool.sks-keyservers.net";
	private final String GET_URL = "/pks/lookup?op=get&search=";
	private final Pattern KEY_PATTERN = 
			Pattern.compile("(-----BEGIN PGP PUBLIC KEY BLOCK-----.*-----END PGP PUBLIC KEY BLOCK-----)", Pattern.DOTALL);
	private final TorNetwork network;
	
	public PgpService(TorNetwork network) {
		this.network = network;
	}
	
	public PGPPublicKey retrieveKey(String username) {
		HttpClient httpClient = this.network.getHttpClient();
		try {
			HttpResponse response = httpClient.execute(
					new HttpHost(HOSTNAME, -1, "https"), new HttpGet(GET_URL + username));
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
}
