package protocol;

public enum DataType {

	USERNAME,			// String
	ADDRESS,			// InetSocketAddress
	ADDRESS_HEADER,		// SignedObject
	CHALLENGE_DATA, 	// byte[]
	CHALLENGE_RESPONSE, // String
	OTR_CHUNK			// String
}
