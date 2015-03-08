package client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Scanner;

import network.TorNetwork;
import protocol.DataType;
import protocol.Message;
import protocol.MessageType;

public final class ClientUtils {
	
	public static String parseHostname(String configDir) {
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
	
	public static InetSocketAddress resolveUser(TorNetwork network, String username) {
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
}
