package client;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.java.otr4j.OtrEngineHost;
import net.java.otr4j.OtrException;
import net.java.otr4j.OtrPolicy;
import net.java.otr4j.OtrPolicyImpl;
import net.java.otr4j.session.FragmenterInstructions;
import net.java.otr4j.session.InstanceTag;
import net.java.otr4j.session.SessionID;
import network.TorNetwork;

public final class ClientOtrEngine implements OtrEngineHost {
	private static ClientOtrEngine otrEngine;
	
	private final OtrPolicy policy;
	private final TorNetwork network;
	private final Map<String, ObjectOutputStream> activeOutputStreams;
	
	public synchronized static ClientOtrEngine getInstance(TorNetwork network) {
		if (otrEngine == null) {
			otrEngine = new ClientOtrEngine(network);
		}
		return otrEngine;
	}
	private ClientOtrEngine(TorNetwork network) {
		this.policy = new OtrPolicyImpl(OtrPolicy.ALLOW_V2 | OtrPolicy.ALLOW_V3
				| OtrPolicy.ERROR_START_AKE);
		this.network = network;
		this.activeOutputStreams = new ConcurrentHashMap<String, ObjectOutputStream>();
	}
	
	@Override
	public void injectMessage(SessionID sessionID, String msg)
			throws OtrException {
		ObjectOutputStream oos = activeOutputStreams.get(sessionID.getUserID());
		if (oos == null) {
			throw new RuntimeException("No session has been started.");
		}
		try {
			oos.writeObject(msg);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void addExistingConnection(String userId, Socket connection) {
		ObjectOutputStream oos;
		try {
			oos = new ObjectOutputStream(connection.getOutputStream());
			activeOutputStreams.put(userId, oos);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public Socket createConnection(SessionID sessionID, Object object) {
		String userID = sessionID.getUserID();
		if (activeOutputStreams.containsKey(userID)) {
			throw new RuntimeException("Connection already exists.");
		}
		String[] split = userID.split(":");
		try {
			Socket socket = network.unsafeConnect(split[0], Integer.parseInt(split[1]));
			ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
			activeOutputStreams.put(userID, oos);
			oos.writeObject(object);
			return socket;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void unreadableMessageReceived(SessionID sessionID)
			throws OtrException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void unencryptedMessageReceived(SessionID sessionID, String msg)
			throws OtrException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void showError(SessionID sessionID, String error)
			throws OtrException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void smpError(SessionID sessionID, int tlvType, boolean cheated)
			throws OtrException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void smpAborted(SessionID sessionID) throws OtrException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void finishedSessionMessage(SessionID sessionID, String msgText)
			throws OtrException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void requireEncryptedMessage(SessionID sessionID, String msgText)
			throws OtrException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public OtrPolicy getSessionPolicy(SessionID sessionID) {
		return this.policy;
	}

	@Override
	public FragmenterInstructions getFragmenterInstructions(SessionID sessionID) {
		return null;
	}

	@Override
	public KeyPair getLocalKeyPair(SessionID sessionID) throws OtrException {
		KeyPairGenerator kg;
		try {
			kg = KeyPairGenerator.getInstance("DSA");
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			return null;
		}
		return kg.genKeyPair();
	}

	@Override
	public byte[] getLocalFingerprintRaw(SessionID sessionID) {
		return null;
	}

	@Override
	public void askForSecret(SessionID sessionID, InstanceTag receiverTag,
			String question) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void verify(SessionID sessionID, String fingerprint, boolean approved) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void unverify(SessionID sessionID, String fingerprint) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public String getReplyForUnreadableMessage(SessionID sessionID) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getFallbackMessage(SessionID sessionID) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void messageFromAnotherInstanceReceived(SessionID sessionID) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void multipleInstancesDetected(SessionID sessionID) {
		// TODO Auto-generated method stub
		
	}

}
