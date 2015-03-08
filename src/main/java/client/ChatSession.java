package client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SignedObject;
import java.util.List;

import org.bouncycastle.util.encoders.Base64Encoder;

import services.KeyManager;
import util.ConsoleHelper;
import util.Util;
import net.java.otr4j.OtrException;
import net.java.otr4j.session.OtrFragmenter;
import net.java.otr4j.session.Session;
import net.java.otr4j.session.SessionID;
import net.java.otr4j.session.SessionStatus;
import network.TorNetwork;

public final class ChatSession {
	private final TorNetwork network;
	private final Session session;
	private final Base64Encoder encoder;
	/*
	 * Starts a new outgoing connection
	 */
	public ChatSession(TorNetwork network, Session session, SignedObject header) {
		this.network = network;
		this.session = session;
		this.encoder = new Base64Encoder();
		startEncryptedSession(session, header);
	}

	public ChatSession(TorNetwork network, Session session, ObjectInputStream inputStream) {
		this.network = network;
		this.session = session;
		this.encoder = new Base64Encoder();
		receiveEncryptedConnection(session, inputStream);
	}
	
	private void startEncryptedSession(Session session, SignedObject header) {
		ClientOtrEngine otrEngine = ClientOtrEngine.getInstance(network);
		KeyManager keyManager = KeyManager.getKeyManager(network);
		SessionID sessionID = session.getSessionID();
		byte[] encrypted = Util.encrypt(header, keyManager.getPgpPublicKey(sessionID.getAccountID()));
		Socket connection = otrEngine.createConnection(sessionID, encrypted);
		try {
			ObjectInputStream ois = new ObjectInputStream(connection.getInputStream());
			session.startSession();
			// 1. DH Commit
			String dhCommit = (String) ois.readObject();
			session.transformReceiving(dhCommit);
			
			// 2. Reveal Signature
			String revealSiganture = (String) ois.readObject();
			session.transformReceiving(revealSiganture);
			
			// 3. User authentication challenge
			String cipherText = (String) ois.readObject();
			String challengeText = processMessage(cipherText);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			encoder.decode(challengeText, baos);
			String plainText = (String) Util.decrypt(baos.toByteArray(), keyManager.getPGPPrivateKey());
			sendMessage(plainText);

			if (session.getSessionStatus() != SessionStatus.ENCRYPTED) {
				session.endSession();
				ConsoleHelper.printRed("Failed to encrypt outgoing session.");
				return;
			}
			startLisenter(ois);
			ConsoleHelper.printGreen("Outgoing session [" + sessionID.getAccountID() + "] encrypted and authenticated.");
		} catch (IOException | OtrException | ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	
	}
	
	private void receiveEncryptedConnection(Session session, ObjectInputStream inputStream) {
		String username = session.getSessionID().getAccountID();
		try {
			// 1. OTR Query
			String query = (String) inputStream.readObject();
			session.transformReceiving(query);
			
			// 2. OTR DH Key
			String dhKey = (String) inputStream.readObject();
			session.transformReceiving(dhKey);
			
			// 3. Signature
			String revealSignature = (String) inputStream.readObject();
			session.transformReceiving(revealSignature);
			
			if (session.getSessionStatus() != SessionStatus.ENCRYPTED) {
				ConsoleHelper.printRed("Failed to encrypt incoming session.");
				session.endSession();
				return;
			}
			// send challenge to verify identity
			KeyManager keyManager = KeyManager.getKeyManager(network);
			String plainText = Util.getRandomChallenge();
			byte[] cipherText = Util.encrypt(plainText, keyManager.getPgpPublicKey(username));
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			encoder.encode(cipherText, 0, cipherText.length, baos);
			String cipherString = new String(baos.toByteArray(), StandardCharsets.UTF_8);
			sendMessage(cipherString);
			
			// verify challenge
			String otrText = (String) inputStream.readObject();
			String challengeResponse = processMessage(otrText);
			if (!plainText.equals(challengeResponse)) {
				ConsoleHelper.printRed("Failed to authenticate incoming session.");
				session.endSession();
				return;
			}

			startLisenter(inputStream);
			ConsoleHelper.printGreen("Incoming session [" + username + "] encrypted and authenticated");
		} catch (IOException | ClassNotFoundException | OtrException e) {
			e.printStackTrace();
		}
	}
	
	public void sendMessage(String message) {
		ClientOtrEngine otrEngine = ClientOtrEngine.getInstance(network);
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			byte[] outgoingBytes = message.getBytes(StandardCharsets.UTF_8);
			encoder.encode(outgoingBytes, 0, outgoingBytes.length, baos);
			String content = new String(baos.toByteArray(), StandardCharsets.UTF_8);
			String[] chunks = session.transformSending(content);
			for (String s : chunks) {
				otrEngine.injectMessage(session.getSessionID(), s);
			}
		} catch (OtrException | IOException e) {
			e.printStackTrace();
		}
	}
	
	public String processMessage(String message) {
		try {
			String base64Message = session.transformReceiving(message);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			encoder.decode(base64Message, baos);
			return new String(baos.toByteArray(), StandardCharsets.UTF_8);
		} catch (OtrException | IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	public void startLisenter(ObjectInputStream inputStream) {
		new Thread(new MessageListener(inputStream)).start();
	}

	private class MessageListener implements Runnable {
		private final ObjectInputStream inputStream;
		private final String username;
		
		public MessageListener(ObjectInputStream inputStream) {
			this.inputStream = inputStream;
			this.username = session.getSessionID().getAccountID();
		}
		
		@Override
		public void run() {
			while (true) {
				try {
					String message = (String) inputStream.readObject();
					System.out.println(username + ": " + processMessage(message));
				} catch (ClassNotFoundException | IOException e) {
					e.printStackTrace();
					break;
				}
			}			
		}
	}
}
