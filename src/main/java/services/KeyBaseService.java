package services;

import java.io.IOException;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.json.JSONException;
import org.json.JSONObject;

import util.Util;
import network.TorNetwork;

public final class KeyBaseService {
	private final String KEYBASE_HOSTNAME = "fncuwbiisyh6ak3i.onion";
	private final int KEYBASE_PORT = 80;
	private final String GET_URL = "/_/api/1.0/user/lookup.json?username=";

	
	private final TorNetwork network;
	
	public KeyBaseService(TorNetwork network) {
		this.network = network;
	}
	
	public PGPPublicKey retrieveKey(String username) throws PGPException {
		HttpClient httpClient = this.network.getHttpClient();
		try {
			HttpResponse response = httpClient.execute(
					new HttpHost(KEYBASE_HOSTNAME, KEYBASE_PORT), new HttpGet(GET_URL + username + "&fields=public_keys"));
			JSONObject obj = new JSONObject(EntityUtils.toString(response.getEntity()));
			int status = obj.getJSONObject("status").getInt("code");
			if (status == 0) {
				JSONObject primaryKey = obj.getJSONObject("them").getJSONObject("public_keys").getJSONObject("primary");
				System.out.println(primaryKey.getString("bundle"));
				return Util.getPGPPublicKey(primaryKey.getString("bundle"));
			}
		} catch (IOException | ParseException | JSONException e) {
			e.printStackTrace();
		}
		return null;
	}
}
