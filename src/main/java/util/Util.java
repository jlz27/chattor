package util;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Iterator;

import org.bouncycastle.openpgp.PGPEncryptedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedDataList;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPLiteralDataGenerator;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.bc.BcPGPObjectFactory;
import org.bouncycastle.openpgp.bc.BcPGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.bc.BcPGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.operator.PBESecretKeyDecryptor;
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyDecryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPDataEncryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider;
import org.bouncycastle.openpgp.operator.bc.BcPublicKeyDataDecryptorFactory;
import org.bouncycastle.openpgp.operator.bc.BcPublicKeyKeyEncryptionMethodGenerator;

public final class Util {

	public static char[] readPassword() {
		// Workaround for running in Eclipse, not completely safe
		if (System.console() == null) {
			BufferedReader bf = new BufferedReader(new InputStreamReader(System.in));
			try {
				char[] pass = bf.readLine().toCharArray();
				return pass;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		System.out.println("Enter server password: ");
		return System.console().readPassword();
	}
	
	@SuppressWarnings("rawtypes")
	public static PGPPublicKey getPGPPublicKey(String key) throws IOException, PGPException {
		BcPGPPublicKeyRingCollection pgpPublicKeyRing = new BcPGPPublicKeyRingCollection(
				PGPUtil.getDecoderStream(new ByteArrayInputStream(key.getBytes(StandardCharsets.UTF_8))));
		Iterator keyRings = pgpPublicKeyRing.getKeyRings();
		while(keyRings.hasNext()) {
			PGPPublicKeyRing keyRing = (PGPPublicKeyRing) keyRings.next();
            Iterator keyIterator = keyRing.getPublicKeys();
            while (keyIterator.hasNext()) {
                PGPPublicKey k = (PGPPublicKey) keyIterator.next();
                if (k.isEncryptionKey()) {
                	return k;
                }
            }
        }
		return null;
	}
	
	@SuppressWarnings("rawtypes")
	public static PGPPrivateKey getPGPPrivateKey(String keyLocation) throws FileNotFoundException, IOException, PGPException {
		BcPGPSecretKeyRingCollection pgpSec = new BcPGPSecretKeyRingCollection(PGPUtil.getDecoderStream(new FileInputStream(keyLocation)));
        Iterator keyRings = pgpSec.getKeyRings();
        PGPSecretKeyRing keyRing = (PGPSecretKeyRing) keyRings.next();
        PBESecretKeyDecryptor decryptor = new BcPBESecretKeyDecryptorBuilder(new BcPGPDigestCalculatorProvider()).build("".toCharArray());
        return keyRing.getSecretKey().extractPrivateKey(decryptor);
	}
	
	
	@SuppressWarnings("unchecked")
	public static Object decrypt(byte[] encrypted, PGPPrivateKey privateKey) {
		try {
	        InputStream in = PGPUtil.getDecoderStream(new ByteArrayInputStream(encrypted));
	
	        BcPGPObjectFactory pgpF = new BcPGPObjectFactory(in);
	        PGPEncryptedDataList enc = null;
	        Object o = pgpF.nextObject();
	
	        if (o instanceof PGPEncryptedDataList) {
	            enc = (PGPEncryptedDataList) o;
	        } else {
	            enc = (PGPEncryptedDataList) pgpF.nextObject();
	        }
	        Iterator<PGPPublicKeyEncryptedData> it = enc.getEncryptedDataObjects();
	        PGPPublicKeyEncryptedData pbe = (PGPPublicKeyEncryptedData) it.next();
	        
	        BcPublicKeyDataDecryptorFactory decryptFactory = new BcPublicKeyDataDecryptorFactory(privateKey);
	        
	        InputStream clear = pbe.getDataStream(decryptFactory);
	        BcPGPObjectFactory pgpFact = new BcPGPObjectFactory(clear);
	        PGPLiteralData ld = (PGPLiteralData) pgpFact.nextObject();
	        InputStream unc = ld.getInputStream();
	
	        ObjectInputStream ois = new ObjectInputStream(unc);
	        return ois.readObject();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
    }

    public static byte[] encrypt(Object obj, PGPPublicKey encKey) {
    	try {
	    	ByteArrayOutputStream clearBytes = new ByteArrayOutputStream();
	    	ObjectOutputStream oos = new ObjectOutputStream(clearBytes);
	    	oos.writeObject(obj);
	    	byte[] byteArray = clearBytes.toByteArray();
	    	oos.close();
	
	    	ByteArrayOutputStream encOut = new ByteArrayOutputStream();
	        OutputStream out = encOut;
	        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
	        PGPLiteralDataGenerator lData = new PGPLiteralDataGenerator();
	        OutputStream pOut = lData.open(bOut, PGPLiteralData.BINARY, PGPLiteralData.CONSOLE, 
	        		byteArray.length, new Date());;
	        
	        pOut.write(byteArray);
	        lData.close();
	        bOut.close();
	        PGPEncryptedDataGenerator generator = new PGPEncryptedDataGenerator(
					new BcPGPDataEncryptorBuilder(PGPEncryptedDataGenerator.AES_256));
			generator.addMethod(new BcPublicKeyKeyEncryptionMethodGenerator(encKey));
			
	        byte[] bytes = bOut.toByteArray();
	        OutputStream cOut = generator.open(out, bytes.length);
	        cOut.write(bytes);
	
	        cOut.close();
	        out.close();
	
	        return encOut.toByteArray();
    	} catch (Exception e) {
        	throw new RuntimeException(e);
        }
    }
}
