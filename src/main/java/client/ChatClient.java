package client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;

import protocol.DataType;
import protocol.Message;
import protocol.MessageType;
import services.KeyManager;
import util.Configuration;
import util.ConsoleHelper;
import util.ObjectSigner;
import util.Util;
import net.java.otr4j.OtrSessionManager;
import net.java.otr4j.OtrSessionManagerImpl;
import net.java.otr4j.session.Session;
import net.java.otr4j.session.SessionID;
import network.TorNetwork;

public final class ChatClient implements Runnable {
	private static final Logger LOGGER = LoggerFactory.getLogger(ChatClient.class);
	private static final String TOR_ADDR = "127.0.0.1";
	private static final int TOR_PORT = 9050;
	private static final String CLIENT_PROTOCOL = "CTORv1";

	private final ServerSocket socket;
	private final TorNetwork network;
	private final InetSocketAddress clientAddr;
	private final OtrSessionManager sessionManager;
	private final Map<String, ChatSession> openSessions;
	
	private String clientName;

	public static void main(String args[]) throws NumberFormatException, IOException, PGPException, KeyStoreException,
			NoSuchAlgorithmException, CertificateException, UnrecoverableEntryException {
		Configuration.initialize(args[0]);
		
		System.setProperty("javax.net.ssl.trustStore", Configuration.TRUSTSTORE);
		System.setProperty("javax.net.ssl.trustStoreType", Configuration.TRUSTSTORE_TYPE);
		System.setProperty("javax.net.ssl.trustStorePassword", "client");
		
		new ChatClient(Configuration.CLIENT_PORT, Configuration.CLIENT_DIR).run();;
	}
	
	public ChatClient(int serverPort, String configDir) throws IOException, PGPException {
		this.socket = new ServerSocket(serverPort);
		this.network = new TorNetwork(TOR_ADDR, TOR_PORT);
		this.clientAddr = new InetSocketAddress(ClientUtils.parseHostname(configDir), serverPort);
		this.sessionManager = new OtrSessionManagerImpl(ClientOtrEngine.getInstance(network));
		this.openSessions = new HashMap<String, ChatSession>();
	}

	public void run() {
		boolean isFinished = false;
		ConsoleHelper.printBlue("Listening on address: " + this.clientAddr);
		Scanner scanner = new Scanner(System.in);
		ConsoleHelper.printCyan("Please log in with username: ");
	    clientName = scanner.nextLine();
	    ConsoleHelper.printBlue("Registering in directory: " + socket.getLocalPort());
		SignedObject header = registerUser(clientName);
		
		new Thread(new ConnectionListener()).start();
		// simple REPL like environment
		ConsoleHelper.printCyan("Ready for commands:");
		while (!isFinished) {
			try {
				String nextLine = scanner.nextLine();
				String[] split = nextLine.split(" ");
				switch(split[0]) {
					case "/m" :
					{
						String destName = split[1];
						if (!this.openSessions.containsKey(destName)) {
							InetSocketAddress destHost = ClientUtils.resolveUser(network, destName);
							SessionID sessionID = new SessionID(destName, destHost.toString(), CLIENT_PROTOCOL);
							Session session = sessionManager.getSession(sessionID);
							ChatSession secureSession = new ChatSession(this.network, session, header);
							this.openSessions.put(destName, secureSession);
						}
						ChatSession chatSession = this.openSessions.get(destName);
						int index = nextLine.indexOf(destName);
						String message = nextLine.substring(index + destName.length() + 1);
						chatSession.sendMessage(message);
						break;
					}
					default: 
						ConsoleHelper.printRed("Unknown command");
				}
			} catch (Exception e) {
				e.printStackTrace();
				ConsoleHelper.printRed("Illegal command");
			}
		}
		scanner.close();

	}
	
	private SignedObject registerUser(String username) {
		KeyManager keyManager = KeyManager.getKeyManager(network);
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
			if (challenge.getType() != MessageType.CHALLENGE) {
				ConsoleHelper.printRed("Did not receive challenge from server");
			}
			byte[] encrypted = (byte[]) challenge.getData().get(DataType.CHALLENGE_DATA);
			String plainText = (String) Util.decrypt(encrypted, keyManager.getPGPPrivateKey());
			Message response = new Message(MessageType.CHALLENGE_RESPONSE);
			response.addData(DataType.CHALLENGE_RESPONSE, plainText);
			oos.writeObject(response);
			
			Message headerResponse = (Message) ois.readObject();
			if (headerResponse.getType() != MessageType.ADDRESS_RESPONSE) {
				ConsoleHelper.printRed("Did not receive address response from server");
			}
			return (SignedObject) headerResponse.getData().get(DataType.ADDRESS_HEADER);
		} catch (IOException | ClassNotFoundException e) {
			ConsoleHelper.printRed("Register user failed");
		}
		return null;
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
				SessionID sessionId = new SessionID(username, address.toString(), CLIENT_PROTOCOL);
				Session session = sessionManager.getSession(sessionId);
				ChatSession secureSession = new ChatSession(network, session, inputStream);
				openSessions.put(username, secureSession);
			} catch (IOException | ClassNotFoundException e) {
				e.printStackTrace();
			}
		}
	}
}
