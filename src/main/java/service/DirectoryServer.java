package service;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import network.SecureMessage;
import network.SecureMessage.Type;
import network.TorAddress;

public final class DirectoryServer {
	private static final int SERVER_PORT = 15000;

	private final ServerSocket socket;
	private final Cache<String, TorAddress> nameAddressCache;
	
	public static void main(String[] args) throws IOException {
		new DirectoryServer().run();
	}
	public DirectoryServer() throws IOException {
		this.socket = new ServerSocket(SERVER_PORT);
		this.nameAddressCache = CacheBuilder.newBuilder().expireAfterWrite(60, TimeUnit.MINUTES).build();
	}

	public void run() {
		System.out.println("Starting directory server on port: " + socket.getLocalPort());
		while(true) {
			try {
				Socket connection = socket.accept();
				new Thread(new ConnectionHandler(connection)).start();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	private class ConnectionHandler implements Runnable {
		private final Socket connection;
		
		public ConnectionHandler(Socket connection) {
			this.connection = connection;
		}
		
		public void run() {
			try {
				final ObjectInputStream inputStream = new ObjectInputStream(this.connection.getInputStream());
				SecureMessage message = (SecureMessage) inputStream.readObject();
				SecureMessage response = null;
				switch(message.getType()) {
				case ADD:
					if (DirectoryServer.this.nameAddressCache.getIfPresent(message.getUsername()) == null) {
						System.out.println("Registering username: " + message.getUsername() + 
								" with address: " + message.getAddress());
						DirectoryServer.this.nameAddressCache.put(message.getUsername(), message.getAddress());
					}
					break;
				case FIND:
					TorAddress address = DirectoryServer.this.nameAddressCache.getIfPresent(message.getUsername());
					if (address != null) {
						response = new SecureMessage(message.getUsername(), address, Type.ADDRESS_RESPONSE);
					} else {
						response = new SecureMessage(null, null, Type.ERROR);
					}
					break;
				default:
					System.err.println("Unknonw directory server operation type: " + message.getType());
				}
				if (response != null) {
					ObjectOutputStream oos = new ObjectOutputStream(this.connection.getOutputStream());
					oos.writeObject(response);
				}
				this.connection.close();
			} catch (ClassNotFoundException | IOException e1) {
			 e1.printStackTrace();
		 }
		}
	}
}
