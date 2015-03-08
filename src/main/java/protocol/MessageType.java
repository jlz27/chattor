package protocol;

public enum MessageType {
		ADD_ADDRESS,		// <USERNAME: string, ADDRESS: InetSocketAddress
		FIND_ADDRESS,		// <USERNAME: string> : ADDRESS_RESPONSE
		CHALLENGE,			// <CHALLENGE_DATA: byte[]>
		CHALLENGE_RESPONSE,	// <CHALLENGE_DATA: byte[]>
		ADDRESS_RESPONSE,	// <ADDRESS_HEADER: SignedObject>
		ERROR_RESPONSE
}