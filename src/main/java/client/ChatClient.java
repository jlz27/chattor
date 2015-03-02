package client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignedObject;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.util.Scanner;

import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import protocol.DataType;
import protocol.Message;
import protocol.MessageType;
import server.ChatServer;
import util.Configuration;
import util.ObjectSigner;
import util.Util;
import network.TorNetwork;

public final class ChatClient implements Runnable {

	private static final Logger LOGGER = LoggerFactory.getLogger(ChatClient.class);
	private static final String TOR_ADDR = "127.0.0.1";
	private static final int TOR_PORT = 9050;

	private final ServerSocket socket;
	private final TorNetwork network;
	private final InetSocketAddress clientAddr;
	private final PGPPrivateKey privateKey;
	private final PublicKey serverPublicKey;

	public static void main(String args[]) throws NumberFormatException, IOException, PGPException, KeyStoreException,
			NoSuchAlgorithmException, CertificateException, UnrecoverableEntryException {
		Configuration.initialize(args[0]);
		
		System.setProperty("javax.net.ssl.trustStore", Configuration.TRUSTSTORE);
		System.setProperty("javax.net.ssl.trustStoreType", Configuration.TRUSTSTORE_TYPE);
		System.setProperty("javax.net.ssl.trustStorePassword", "client");
		
		KeyStore ks = KeyStore.getInstance(Configuration.TRUSTSTORE_TYPE);
		FileInputStream keyStoreFile = new FileInputStream(Configuration.TRUSTSTORE);
		ks.load(keyStoreFile, "client".toCharArray());
		KeyStore.TrustedCertificateEntry pkEntry = (KeyStore.TrustedCertificateEntry) ks.getEntry("server_key", null);
		PublicKey serverPublicKey = pkEntry.getTrustedCertificate().getPublicKey();
		
		new ChatClient(serverPublicKey, Configuration.CLIENT_PORT, Configuration.CLIENT_DIR).run();;
	}
	
	public ChatClient(PublicKey serverPublicKey, int serverPort, String configDir) throws IOException, PGPException {
		this.socket = new ServerSocket(serverPort);
		this.network = new TorNetwork(TOR_ADDR, TOR_PORT);
		this.clientAddr = new InetSocketAddress(parseHostname(configDir), serverPort);
		this.privateKey = Util.getPGPPrivateKey(Configuration.SECRET_KEY);
		this.serverPublicKey = serverPublicKey;
	}

	public void run() {
		System.out.println("Listening on port: " + socket.getLocalPort());
		Scanner scanner = new Scanner(System.in);
		System.out.println("Please log in with username: ");
	    String username = scanner.nextLine();
	    System.out.println("Registering in directory: " + socket.getLocalPort());
		SignedObject header = registerUser(username);
		Message signedHeader = (Message) ObjectSigner.verifyObject(header, this.serverPublicKey);
		new Thread(new ChatSession(this.network, scanner)).start();
		while(true) {
			try {
				Socket connection = socket.accept();
				new Thread(new ConnectionHandler(connection)).start();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	private SignedObject registerUser(String username) {
		Socket directoryConnection;
		try {
			directoryConnection = this.network.openDirectoryConnection();
			ObjectOutputStream oos = new ObjectOutputStream(directoryConnection.getOutputStream());
			ObjectInputStream ois = new ObjectInputStream(directoryConnection.getInputStream());
			Message addMessage = new Message(MessageType.ADD_ADDRESS);
			addMessage.addData(DataType.USERNAME, username);
			addMessage.addData(DataType.ADDRESS, clientAddr);
			oos.writeObject(addMessage);
			
			// read challege from server
			Message challenge = (Message) ois.readObject();
			if (challenge.getType() != MessageType.ADD_CHALLENGE) {
				LOGGER.error("Did not receive challenge from server");
			}
			byte[] encrypted = (byte[]) challenge.getData().get(DataType.CHALLENGE_DATA);
			String plainText = (String) Util.decrypt(encrypted, this.privateKey);
			Message response = new Message(MessageType.CHALLENGE_RESPONSE);
			response.addData(DataType.CHALLENGE_RESPONSE, plainText);
			oos.writeObject(response);
			
			Message headerResponse = (Message) ois.readObject();
			if (headerResponse.getType() != MessageType.ADDRESS_RESPONSE) {
				LOGGER.error("Did not receive address response from server");
			}
			return (SignedObject) headerResponse.getData().get(DataType.ADDRESS_HEADER);
		} catch (IOException | ClassNotFoundException e) {
			LOGGER.error("Register user failed");
		}
		return null;
	}
	
	private String parseHostname(String configDir) {
		String hostnameFile = "hostname";
		String hostname = null;
		Scanner scanner;
		try {
			scanner = new Scanner(new FileInputStream(new File(configDir + File.separator + hostnameFile)));
			hostname = scanner.nextLine();
			scanner.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		return hostname;
	}
	
	private static class ConnectionHandler implements Runnable {
		private final Socket connection;
		
		public ConnectionHandler(Socket connection) {
			this.connection = connection;
		}
		
		public void run() {
			try {
				final InputStreamReader inputStream = new InputStreamReader(this.connection.getInputStream());
				char[] buffer = new char[1024];
				int charsRead = 0;
				while((charsRead = inputStream.read(buffer)) != -1) {
					System.out.print(this.connection.getInetAddress() + ": ");
					for (int i = 0; i < charsRead; ++i) {
						System.out.print(buffer[i]);
					}
					System.out.println();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
