package client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SignedObject;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import org.bouncycastle.openpgp.PGPException;

import protocol.DataType;
import protocol.Message;
import services.KeyManager;
import util.Configuration;
import util.ConsoleHelper;
import util.ObjectSigner;
import util.Util;
import net.java.otr4j.OtrException;
import net.java.otr4j.session.Session;
import net.java.otr4j.session.SessionID;
import net.java.otr4j.session.SessionImpl;
import network.TorNetwork;

public final class ChatClient implements Runnable {
	private static final String TOR_ADDR = "127.0.0.1";
	private static final int TOR_PORT = 9050;
	private static final String CLIENT_PROTOCOL = "CTORv1";

	private static ChatClient client;
	
	private final ServerSocket socket;
	private final TorNetwork network;
	private final InetSocketAddress clientAddr;
	private final Map<String, ChatSession> openSessions;
	
	private String clientName;

	public static void main(String args[]) throws NumberFormatException, IOException, PGPException, KeyStoreException,
			NoSuchAlgorithmException, CertificateException, UnrecoverableEntryException {
		Configuration.initialize(args[0]);
		
		System.setProperty("javax.net.ssl.trustStore", Configuration.TRUSTSTORE);
		System.setProperty("javax.net.ssl.trustStoreType", Configuration.TRUSTSTORE_TYPE);
		System.setProperty("javax.net.ssl.trustStorePassword", "client");
		
		client = new ChatClient(Configuration.CLIENT_PORT, Configuration.CLIENT_DIR);
		client.run();
	}
	
	public static ChatClient getInstance() {
		return client;
	}
	
	private ChatClient(int serverPort, String configDir) throws IOException, PGPException {
		this.socket = new ServerSocket(serverPort);
		this.network = new TorNetwork(TOR_ADDR, TOR_PORT);
		this.clientAddr = new InetSocketAddress(ClientUtils.parseHostname(configDir), serverPort);
		this.openSessions = new HashMap<String, ChatSession>();
	}

	public void run() {
		boolean isFinished = false;
		ConsoleHelper.printBlue("Listening on address: " + this.clientAddr);
		Scanner scanner = new Scanner(System.in);
		ConsoleHelper.printCyan("Please log in with username: ");
	    clientName = scanner.nextLine();
	    ConsoleHelper.printBlue("Registering in directory: " + socket.getLocalPort());
		SignedObject header = ClientUtils.registerUser(network, clientAddr, clientName);
		if (header == null) {
			System.exit(1);
		}
		new Thread(new ConnectionListener()).start();
		// simple REPL like environment
		ConsoleHelper.printCyan("Use /n <user name> <message> to start new conversation.");
		String curUser = null;
		while (!isFinished) {
			try {
				String nextLine = scanner.nextLine();
				String[] split = nextLine.split(" ");
				switch(split[0]) {
					case "/n" :
					{
						String destName = split[1];
						if (this.clientName.equals(destName)) {
							ConsoleHelper.printRed("Cannot open connection to self.");
							continue;
						}
						if (!this.openSessions.containsKey(destName)) {
							InetSocketAddress destHost = ClientUtils.resolveUser(network, destName);
							if (destHost == null) {
								ConsoleHelper.printRed("Unknown user: " + destName);
								continue;
							}
							SessionID sessionID = new SessionID(destName, destHost.toString(), CLIENT_PROTOCOL);
							Session session = new SessionImpl(sessionID, ClientOtrEngine.getInstance(network));
							ChatSession secureSession = new ChatSession(this.network, session);
							if (!secureSession.startEncryptedSession(header)) {
								continue;
							}
							this.openSessions.put(destName, secureSession);
						} else {
							ConsoleHelper.printGreen("Using existing connection.", true);
						}
						curUser = destName;
						if (split.length > 2) {
							ChatSession chatSession = this.openSessions.get(destName);
							int index = nextLine.indexOf(destName);
							String message = nextLine.substring(index + destName.length() + 1);
							chatSession.sendMessage(message);
						}
						break;
					}
					default:
					{
						if (curUser == null) {
							ConsoleHelper.printRed("No currently active conversation");
							continue;
						}
						ChatSession chatSession = this.openSessions.get(curUser);
						if (chatSession == null) {
							ConsoleHelper.printRed("Session [" + curUser + "] is not currently connected.");
							continue;
						}
						chatSession.sendMessage(nextLine);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
				ConsoleHelper.printRed("An error has occurred during the previous operation.");
			}
		}
		scanner.close();

	}
	
	public synchronized void removeSession(SessionID sessionId) {
		if (sessionId != null) {
			ClientOtrEngine otrEngine = ClientOtrEngine.getInstance(network);
			
			otrEngine.removeConnection(sessionId.getUserID());
			this.openSessions.remove(sessionId.getAccountID());
		}
	}

	private class ConnectionListener implements Runnable {
		@Override
		public void run() {
			while(true) {
				try {
					Socket connection = socket.accept();
					new Thread(new ConnectionHandler(connection)).start();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}			
		}
	}
	
	private class ConnectionHandler implements Runnable {
		private final Socket connection;
		
		public ConnectionHandler(Socket connection) {
			this.connection = connection;
		}
		
		public void run() {
			ClientOtrEngine otrEngine = ClientOtrEngine.getInstance(network);
			KeyManager keyManager = KeyManager.getKeyManager(network);
			ObjectInputStream inputStream;
			SessionID sessionId = null;
			try {
				inputStream = new ObjectInputStream(this.connection.getInputStream());
				
				// parse encrypted and signed header
				byte[] encryptedHeader = (byte[]) inputStream.readObject();
				SignedObject signedObj = (SignedObject) Util.decrypt(encryptedHeader, keyManager.getPGPPrivateKey());
				Message addrMsg = (Message) ObjectSigner.verifyObject(signedObj,
						KeyManager.getKeyManager(network).getServerKey());
				
				Map<DataType, Serializable> dataMap = addrMsg.getData();
				String username = (String) dataMap.get(DataType.USERNAME);
				InetSocketAddress address = (InetSocketAddress) dataMap.get(DataType.ADDRESS);
				otrEngine.addExistingConnection(address.toString(), connection);
				sessionId = new SessionID(username, address.toString(), CLIENT_PROTOCOL);
				if (openSessions.containsKey(username)) {
					throw new IllegalStateException("Multiple incoming connections from same host.");
				}
				Session session = new SessionImpl(sessionId, ClientOtrEngine.getInstance(network));
				ChatSession secureSession = new ChatSession(network, session);
				if (secureSession.receiveEncryptedConnection(inputStream)) {
					openSessions.put(username, secureSession);
				}
			} catch (Exception e) {
				try {
					connection.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				removeSession(sessionId);
				e.printStackTrace();
			}
		}
	}
}
