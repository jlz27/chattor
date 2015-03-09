package client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.SignedObject;
import java.util.Scanner;

import network.TorNetwork;
import protocol.DataType;
import protocol.Message;
import protocol.MessageType;
import services.KeyManager;
import util.ConsoleHelper;
import util.Util;

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
			throw new RuntimeException(e);
		}
	}
	
	public static SignedObject registerUser(TorNetwork network, InetSocketAddress clientAddr, String username) {
		KeyManager keyManager = KeyManager.getKeyManager(network);
		Socket directoryConnection;
		try {
			directoryConnection = network.openDirectoryConnection();
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
}
