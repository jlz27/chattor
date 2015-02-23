package server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;

import protocol.DataType;
import protocol.Message;
import protocol.MessageType;
import util.Configuration;
import util.Util;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;


public final class ChatServer {
	private static final int SERVER_PORT = 15000;

	private SSLServerSocket serverSocket;
	private Cache<String, InetSocketAddress> nameAddressCache;
	private ExecutorService threadPool;
	
	public static void main(String[] args) throws IOException {
		Configuration.initialize(args[0]);
		char[] password = Util.readPassword();
		System.setProperty("javax.net.ssl.keyStore", Configuration.KEYSTORE);
		System.setProperty("javax.net.ssl.keyStoreType", Configuration.KEYSTORE_TYPE);
		System.setProperty("javax.net.ssl.keyStorePassword", new String(password));
		
		new ChatServer(Configuration.MAX_CONNECTIONS).run();
	}
	
	public ChatServer(int maxThreads) throws IOException {
		this.threadPool = Executors.newFixedThreadPool(maxThreads);
		initializeServer(SERVER_PORT, 3600);
	}

	public void run() {
		System.out.println("Starting directory server on port: " + serverSocket.getLocalPort());
		while(true) {
			try {
				Socket connection = serverSocket.accept();
				this.threadPool.execute(new ConnectionHandler(connection));
			} catch (IOException e) {
				System.err.println("Network error: bad connection.");
			}
		}
	}
	
	/*
	 * Sets up the server network SSL settings. The settings are:
	 * Cipher Suite         : TLS_DHE_RSA_WITH_AES_128_CBC_SHA
	 * Protocol             : TLSV1.2
	 */
	public void initializeServer(int serverPort, int sessionTimeout) throws IOException {
		this.serverSocket = (SSLServerSocket) SSLServerSocketFactory
					.getDefault().createServerSocket(serverPort);
		serverSocket.setEnabledProtocols(new String[]{"TLSv1.2"});
		serverSocket.setEnabledCipherSuites(new String[]{"TLS_RSA_WITH_AES_128_CBC_SHA256"});
		// Do not require client authentication via SSL
		serverSocket.setNeedClientAuth(false);
		this.nameAddressCache = CacheBuilder.newBuilder().expireAfterWrite(sessionTimeout, TimeUnit.SECONDS).build();
	}

	
	private class ConnectionHandler implements Runnable {
		private final Socket connection;
		
		public ConnectionHandler(Socket connection) {
			this.connection = connection;
		}
		
		public void run() {
			try {
				final ObjectInputStream inputStream = new ObjectInputStream(this.connection.getInputStream());
				Message message = (Message) inputStream.readObject();
				Map<DataType, Serializable> dataMap = message.getData();
				Message response = null;
				switch(message.getType()) {
				case ADD_ADDRESS: {
					String username = (String) dataMap.get(DataType.USERNAME);
					InetSocketAddress address = (InetSocketAddress) dataMap.get(DataType.ADDRESS);
					if (ChatServer.this.nameAddressCache.getIfPresent(username) == null) {
						System.out.println("Registering username: " + username + 
								" with address: " + address);
						ChatServer.this.nameAddressCache.put(username, address);
					}
					break;
				}
				case FIND_ADDRESS: {
					String username = (String) dataMap.get(DataType.USERNAME);
					InetSocketAddress address = ChatServer.this.nameAddressCache.getIfPresent(username);
					response = new Message(MessageType.ADDRESS_RESPONSE);
					response.addData(DataType.USERNAME, username);
					response.addData(DataType.ADDRESS, address);
					break;
				}
				default:
					System.err.println("Unknonw directory server operation type: " + message.getType());
				}
				if (response != null) {
					ObjectOutputStream oos = new ObjectOutputStream(this.connection.getOutputStream());
					oos.writeObject(response);
				}
				this.connection.close();
			} catch (ClassNotFoundException | IOException e1) {
			 System.err.println("Network error: invalid connection.");
		 }
		}
	}
}
