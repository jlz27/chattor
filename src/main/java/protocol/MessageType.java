package protocol;

public enum MessageType {
		ADD_ADDRESS,		// <USERNAME: string, ADDRESS: InetSocketAddress, KEYLIST: List<KeyResolver> : ADDRESS_CHALLENGE
		FIND_ADDRESS,		// <USERNAME: string> : ADDRESS_RESPONSE
		ADDRESS_CHALLENGE,	// <USERNAME: string>
		ADDRESS_RESPONSE,	// <USERNAME: string, ADDRESS: InetSocketAddress>
		ERROR_RESPONSE
}