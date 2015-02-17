package chat;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Scanner;

import network.SecureMessage;
import network.SecureMessage.Type;
import network.TorAddress;
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
			TorAddress destAddr = null;
			while (destAddr == null) {
				System.out.println("Enter username: ");
			    String username = scanner.nextLine();
			    destAddr = resolveUser(username);
		    }
		    Socket connect;
			try {
				connect = network.connect(destAddr.getAddress(), destAddr.getPort());
				sendMessage(connect, scanner);
			} catch (IOException e) {
				e.printStackTrace();
			}
		    scanner.close();
		}
	}
	
	private TorAddress resolveUser(String username) {
		try {
			Socket socket = network.openDirectoryConnection();
			ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
			oos.writeObject(new SecureMessage(username, null, Type.FIND));
			ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
			SecureMessage message = (SecureMessage) ois.readObject();
			return message.getAddress();
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
