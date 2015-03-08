package server;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.SignedObject;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;

import org.bouncycastle.openpgp.PGPPublicKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import network.TorNetwork;
import protocol.DataType;
import protocol.Message;
import protocol.MessageType;
import services.KeyManager;
import services.PgpService;
import util.Configuration;
import util.ObjectSigner;
import util.Util;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;


public final class ChatServer {
	private static final String TOR_ADDR = "127.0.0.1";
	private static final int TOR_PORT = 9050;
	private static final int SERVER_PORT = 15000;
	private static final Logger LOGGER = LoggerFactory.getLogger(ChatServer.class);

	private PrivateKey privateKey;
	private TorNetwork torNetwork;
	private SSLServerSocket serverSocket;
	private Cache<String, InetSocketAddress> nameAddressCache;
	private ExecutorService threadPool;
	
	public static void main(String[] args) throws IOException, KeyStoreException,
			NoSuchAlgorithmException, CertificateException, UnrecoverableEntryException {
		Configuration.initialize(args[0]);
		char[] password = Util.readPassword();
		System.setProperty("javax.net.ssl.keyStore", Configuration.KEYSTORE);
		System.setProperty("javax.net.ssl.keyStoreType", Configuration.KEYSTORE_TYPE);
		System.setProperty("javax.net.ssl.keyStorePassword", new String(password));
		
		KeyStore ks = KeyStore.getInstance(Configuration.KEYSTORE_TYPE);
		FileInputStream keyStoreFile = new FileInputStream(Configuration.KEYSTORE);
		ks.load(keyStoreFile, password);
		KeyStore.ProtectionParameter protParam = new KeyStore.PasswordProtection(password);
		KeyStore.PrivateKeyEntry pkEntry = (KeyStore.PrivateKeyEntry) ks.getEntry(Configuration.SECRET_KEY, protParam);
		PrivateKey myPrivateKey = pkEntry.getPrivateKey();
		
		new ChatServer(myPrivateKey, Configuration.MAX_CONNECTIONS).run();
	}
	
	public ChatServer(PrivateKey privateKey, int maxThreads) throws IOException {
		this.privateKey = privateKey;
		this.threadPool = Executors.newFixedThreadPool(maxThreads);
		this.torNetwork = new TorNetwork(TOR_ADDR, TOR_PORT);
		// session timeout set to be 1 hour
		initializeServer(SERVER_PORT, 3600);
	}

	public void run() {
		System.out.println("Starting directory server on port: " + serverSocket.getLocalPort());
		while(true) {
			try {
				Socket connection = serverSocket.accept();
				// set timeout to be 1 minute
				connection.setSoTimeout(1000*60);
				this.threadPool.execute(new ConnectionHandler(connection));
			} catch (IOException e) {
				LOGGER.error("Network error: bad connection.");
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
		
		private boolean sendChallenge(String username, ObjectInputStream ois, ObjectOutputStream oos) {
			KeyManager keyService = KeyManager.getKeyManager(torNetwork);
			try {
				String plainText = Util.getRandomChallenge();
				Message challenge = new Message(MessageType.CHALLENGE);
				byte[] cipherText = Util.encrypt(plainText, keyService.getPgpPublicKey(username));
				challenge.addData(DataType.CHALLENGE_DATA, cipherText);
				oos.writeObject(challenge);
				
				// read challenge response
				Message response = (Message) ois.readObject();
				if (response.getType() != MessageType.CHALLENGE_RESPONSE) {
					return false;
				}
				String responseText = (String) response.getData().get(DataType.CHALLENGE_RESPONSE);
				return plainText.equals(responseText);
			} catch (IOException | ClassNotFoundException e) {
				LOGGER.debug("add address challenge error");
			}
			return false;
		}
		
		public void run() {
			try {
				final ObjectInputStream inputStream = new ObjectInputStream(this.connection.getInputStream());
				ObjectOutputStream oos = new ObjectOutputStream(this.connection.getOutputStream());
				Message message = (Message) inputStream.readObject();
				Map<DataType, Serializable> dataMap = message.getData();
				Message response = null;
				switch(message.getType()) {
				case ADD_ADDRESS: {
					String username = (String) dataMap.get(DataType.USERNAME);
					// verify username via challenge
					if (sendChallenge(username, inputStream, oos)) {
						InetSocketAddress address = (InetSocketAddress) dataMap.get(DataType.ADDRESS);
						System.out.println("Registering username: " + username + 
								" with address: " + address);
						ChatServer.this.nameAddressCache.put(username, address);
						Message header = new Message(MessageType.ADD_ADDRESS);
						header.addData(DataType.USERNAME, username);
						header.addData(DataType.ADDRESS, address);
						SignedObject signedObj = ObjectSigner.signObject(header, ChatServer.this.privateKey);
						response = new Message(MessageType.ADDRESS_RESPONSE);
						response.addData(DataType.ADDRESS_HEADER, signedObj);
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
					oos.writeObject(response);
				}
				this.connection.close();
			} catch (ClassNotFoundException | IOException e1) {
			 System.err.println("Network error: invalid connection.");
		 }
		}
	}
}
