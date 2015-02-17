package service;

import java.io.IOException;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import network.TorNetwork;

public final class KeyService {
	private final String KEYBASE_HOSTNAME = "fncuwbiisyh6ak3i.onion";
	private final int KEYBASE_PORT = 80;
	private final String GET_URL = "/_/api/1.0/user/lookup.json?username=";

	
	private final TorNetwork network;
	
	public KeyService(TorNetwork network) {
		this.network = network;
	}
	
	public String retrieveKey(String username) {
		HttpClient httpClient = this.network.getHttpClient();
		try {
			HttpResponse response = httpClient.execute(
					new HttpHost(KEYBASE_HOSTNAME, KEYBASE_PORT), new HttpGet(GET_URL + "jason&fields=public_keys"));
			JSONObject obj = new JSONObject(EntityUtils.toString(response.getEntity()));
			int status = obj.getJSONObject("status").getInt("code");
			if (status == 0) {
				JSONObject primaryKey = obj.getJSONObject("them").getJSONObject("public_keys").getJSONObject("primary");
				System.out.println(primaryKey);
			} else {
				return null;
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return "bla";
	}
}
