package client;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Scanner;

import protocol.DataType;
import protocol.Message;
import protocol.MessageType;
import network.TorNetwork;

public final class ChatSession implements Runnable {

	private final TorNetwork network;
	private final Scanner scanner;
	
	public ChatSession(TorNetwork network, Scanner scanner) {
		this.network = network;
		this.scanner = scanner;
	}
	
	public void run() {
		while(true) {
			InetSocketAddress destAddr = null;
			while (destAddr == null) {
				System.out.println("Enter username: ");
			    String username = scanner.nextLine();
			    destAddr = resolveUser(username);
		    }
		    Socket connect;
			try {
				connect = network.unsafeConnect(destAddr.getHostString(), destAddr.getPort());
				sendMessage(connect, scanner);
			} catch (IOException e) {
				e.printStackTrace();
			}
		    scanner.close();
		}
	}
	
	private InetSocketAddress resolveUser(String username) {
		try {
			Socket socket = network.openDirectoryConnection();
			ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
			Message findMessage = new Message(MessageType.FIND_ADDRESS);
			findMessage.addData(DataType.USERNAME, username);
			oos.writeObject(findMessage);
			ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
			Message message = (Message) ois.readObject();
			if (message.getType() != MessageType.ADDRESS_RESPONSE) {
				throw new IllegalStateException("Unexpected message type: " + message.getType());
			}
			return (InetSocketAddress) message.getData().get(DataType.ADDRESS);
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public static void sendMessage(Socket connect, Scanner scanner) throws IOException {
		DataOutputStream os = new DataOutputStream(connect.getOutputStream());
		while (!connect.isClosed()) {
			String line = scanner.nextLine();
			if (line.equals("close")) {
				os.close();
				return;
			}
			os.writeBytes(line);
			os.flush();
		}
	}
}
