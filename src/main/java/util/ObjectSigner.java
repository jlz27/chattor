package util;

import java.io.IOException;
import java.io.Serializable;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.SignedObject;

public final class ObjectSigner {
	private static Signature SIGNATURE_ENGINE;
	
	static {
		try {
			SIGNATURE_ENGINE = Signature.getInstance("SHA256withRSA");
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
	}
	
	public static SignedObject signObject(Serializable obj, PrivateKey privateKey) {
		try {
			return new SignedObject(obj, privateKey, SIGNATURE_ENGINE);
		} catch (InvalidKeyException | SignatureException | IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public static Object verifyObject(SignedObject obj, PublicKey publicKey) {
		try {
			obj.verify(publicKey, SIGNATURE_ENGINE);
			return obj.getObject();
		} catch (InvalidKeyException | SignatureException | ClassNotFoundException | IOException e) {
			e.printStackTrace();
		}
		return null;
	}
}
