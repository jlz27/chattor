package network;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.apache.http.client.HttpClient;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import util.ConsoleHelper;

public final class TorNetwork {
	private static final String DIRECTORY_ADDRESS = "n2bfjefdozxmn76n.onion";
	private static final int DIRECTORY_PORT = 15000;
	
	private final InetSocketAddress socksAddr;
	
	private final HttpClient client;
	private final SSLSocketFactory socketFactory;
	
	public TorNetwork(String proxyAddr, int proxyPort) {
		this.socksAddr = new InetSocketAddress(proxyAddr, proxyPort);
		this.socketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
		Registry<ConnectionSocketFactory> reg = RegistryBuilder.<ConnectionSocketFactory>create()
		        .register("https", new SocksSocketFactory(SSLContexts.createDefault(), socksAddr))
		        .build();
		PoolingHttpClientConnectionManager pm = new PoolingHttpClientConnectionManager(reg, new FakeDNSResolver());
		this.client = HttpClients.custom().setConnectionManager(pm).build();
	}
	
	public HttpClient getHttpClient() {
		return client;
	}
	
	public Socket openDirectoryConnection() throws IOException {
		return secureConnect(DIRECTORY_ADDRESS, DIRECTORY_PORT);
	}
	
	public Socket secureConnect(String targetHostname, int targetPort) 
			throws IOException {
		Socket proxySocket = unsafeConnect(targetHostname, targetPort);
		SSLSocket socket = (SSLSocket) this.socketFactory.createSocket(proxySocket,
				this.socksAddr.getHostName(), this.socksAddr.getPort(), true);
		socket.setEnabledProtocols(new String[]{"TLSv1.2"});
		socket.setEnabledCipherSuites(new String[]{"TLS_RSA_WITH_AES_128_CBC_SHA256"});
		return socket;
	}
	
	public Socket unsafeConnect(String targetHostname, int targetPort) throws IOException {
		ConsoleHelper.printBlue("Opening connection to " + targetHostname + ":" + targetPort
				+ " via proxy " + this.socksAddr);
		Proxy proxy = new Proxy(Proxy.Type.SOCKS, socksAddr);
		Socket proxySocket = new Socket(proxy);
		proxySocket.connect(new InetSocketAddress(targetHostname, targetPort));
		return proxySocket;
	}
}
