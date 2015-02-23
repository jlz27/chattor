package client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;

import protocol.DataType;
import protocol.Message;
import protocol.MessageType;
import services.KeyBaseService;
import util.Configuration;
import network.TorNetwork;

public final class ChatClient implements Runnable {
	private static final String TOR_ADDR = "127.0.0.1";
	private static final int TOR_PORT = 9050;

	private final ServerSocket socket;
	private final TorNetwork network;
	private final InetSocketAddress clientAddr;

	public static void main(String args[]) throws NumberFormatException, IOException {
		Configuration.initialize(args[0]);
		
		System.setProperty("javax.net.ssl.trustStore", Configuration.TRUSTSTORE);
		System.setProperty("javax.net.ssl.trustStoreType", Configuration.TRUSTSTORE_TYPE);
		System.setProperty("javax.net.ssl.trustStorePassword", "client");
		
		new ChatClient(Configuration.CLIENT_PORT, Configuration.CLIENT_DIR).run();;
	}
	
	public ChatClient(int serverPort, String configDir) throws IOException {
		this.socket = new ServerSocket(serverPort);
		this.network = new TorNetwork(TOR_ADDR, TOR_PORT);
		this.clientAddr = new InetSocketAddress(parseHostname(configDir), serverPort);
	}

	public void run() {
		System.out.println("Listening on port: " + socket.getLocalPort());
		Scanner scanner = new Scanner(System.in);
		System.out.println("Please log in with username: ");
	    String username = scanner.nextLine();
	    KeyBaseService keyService = new KeyBaseService(network);
	    keyService.retrieveKey(username);
	    System.out.println("Registering in directory: " + socket.getLocalPort());
		registerUser(username);
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
	
	private void registerUser(String username) {
		Socket directoryConnection;
		try {
			directoryConnection = this.network.openDirectoryConnection();
			ObjectOutputStream oos = new ObjectOutputStream(directoryConnection.getOutputStream());
			Message addMessage = new Message(MessageType.ADD_ADDRESS);
			addMessage.addData(DataType.USERNAME, username);
			addMessage.addData(DataType.ADDRESS, clientAddr);
			oos.writeObject(addMessage);
		} catch (IOException e) {
			e.printStackTrace();
		}
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
