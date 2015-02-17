package network;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import org.apache.http.client.HttpClient;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

public final class TorNetwork {
	private static final byte TOR_CONNECT = (byte) 0x01;
	private static final byte SOCKS_VERSION = (byte) 0x04;
	private static final byte SOCKS_DELIM = (byte) 0x00;
	private static final int SOCKS4A_FAKEIP = (int) 0x01;
	
	private static final String DIRECTORY_ADDRESS = "n2bfjefdozxmn76n.onion";
	private static final int DIRECTORY_PORT = 15000;
	
	private final String proxyAddr;
	private final int proxyPort;
	
	private final HttpClient client;
	
	public TorNetwork(String proxyAddr, int proxyPort) {
		this.proxyAddr = proxyAddr;
		this.proxyPort = proxyPort;
		
		InetSocketAddress socksaddr = new InetSocketAddress(proxyAddr, proxyPort);
		Registry<ConnectionSocketFactory> reg = RegistryBuilder.<ConnectionSocketFactory>create()
		        .register("http", new SocksSocketFactory(socksaddr))
		        .build();
		PoolingHttpClientConnectionManager pm = new PoolingHttpClientConnectionManager(reg, new FakeDNSResolver());
		this.client = HttpClients.custom().setConnectionManager(pm).build();
	}
	
	public HttpClient getHttpClient() {
		return client;
	}
	public Socket openDirectoryConnection() throws IOException {
		return connect(DIRECTORY_ADDRESS, DIRECTORY_PORT);
	}
	
	public Socket connect(String targetHostname, int targetPort) 
			throws IOException {
		System.out.println("Opening connection to " + targetHostname + ":" + targetPort
				+ " via proxy " + proxyAddr + ":" + proxyPort);
		Socket socket = new Socket(this.proxyAddr, this.proxyPort);
		DataOutputStream os = new DataOutputStream(socket.getOutputStream());
		os.writeByte(SOCKS_VERSION);
		os.writeByte(TOR_CONNECT);
		os.writeShort(targetPort);
		os.writeInt(SOCKS4A_FAKEIP);
		os.writeByte(SOCKS_DELIM);
		os.writeBytes(targetHostname);
		os.writeByte(SOCKS_DELIM);
		DataInputStream is = new DataInputStream(socket.getInputStream());
		// check status to make sure connection succeeded
		is.readByte();
		byte status = is.readByte();
		if(status != (byte) 90) {    
			//failed for some reason, return useful exception
			throw(new IOException(parseSOCKSStatus(status)));
		}
		// read port and addr since the end application does not care about these values
		is.readShort();
		is.readInt();
		return socket;
	}
	
	private static String parseSOCKSStatus(byte status) {
		String statusString;
		switch(status) {
		case 90:  
			statusString = status + " Request granted.";
			break;
		case 91:
			statusString = status+" Request rejected/failed - unknown reason.";
			break;
		case 92:
			statusString = status+" Request rejected: SOCKS server cannot connect to identd on the client.";
			break;
		case 93:
			statusString = status+" Request rejected: the client program and identd report different user-ids.";
			break;
		default:
			statusString = status+" Unknown SOCKS status code.";                  
		}
		return(statusString);

	}
}
