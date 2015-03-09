package services;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.Map;

import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;

import network.TorNetwork;
import util.Configuration;
import util.Util;

public final class KeyManager {
	private static KeyManager keyManager;

	private final PgpKeyService keyService;
	private final Map<String, PGPPublicKey> keyStore;
	
	private PGPPrivateKey privateKey;
	private PublicKey serverKey;

	private KeyManager(TorNetwork network) {
	
		this.keyService = new PgpKeyService(network);
		this.keyStore = new HashMap<String, PGPPublicKey>();
	}
	
	public static KeyManager getKeyManager(TorNetwork network) {
		if (keyManager == null) {
			keyManager = new KeyManager(network);
		}
		
		return keyManager;
	}
	
	public PGPPublicKey getPgpPublicKey(String username) {
		if (!this.keyStore.containsKey(username)) {
			PGPPublicKey pubKey = this.keyService.retrieveKey(username);
			this.keyStore.put(username, pubKey);
		}
		return this.keyStore.get(username);
	}
	
	public PGPPrivateKey getPGPPrivateKey() {
		if (privateKey == null) {
			try {
				privateKey = Util.getPGPPrivateKey(Configuration.SECRET_KEY);
			} catch (IOException | PGPException e) {
				throw new RuntimeException(e);
			}
		}
		return privateKey;
	}
	
	public PublicKey getServerKey() {
		if (this.serverKey == null) {
			KeyStore ks;
			try {
				ks = KeyStore.getInstance(Configuration.TRUSTSTORE_TYPE);
				FileInputStream keyStoreFile = new FileInputStream(Configuration.TRUSTSTORE);
				ks.load(keyStoreFile, "client".toCharArray());
				KeyStore.TrustedCertificateEntry pkEntry = (KeyStore.TrustedCertificateEntry) ks.getEntry("server_key", null);
				PublicKey serverPublicKey = pkEntry.getTrustedCertificate().getPublicKey();
				this.serverKey = serverPublicKey;
			} catch (KeyStoreException | NoSuchAlgorithmException
					| CertificateException | IOException | UnrecoverableEntryException e) {
				throw new RuntimeException(e);
			}
		}
		return serverKey;
	}
}
