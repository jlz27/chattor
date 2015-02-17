package network;

import java.io.Serializable;

public final class SecureMessage implements Serializable {
	private static final long serialVersionUID = 1L;

	public enum Type {
		ADD,
		FIND,
		ADDRESS_RESPONSE,
		ERROR
	}
	
	private final String username;
	private final TorAddress address;
	private final Type type;
	
	public SecureMessage(String username, TorAddress address, Type type) {
		this.username = username;
		this.address = address;
		this.type = type;
	}
	
	public String getUsername() {
		return username;
	}
	
	public TorAddress getAddress() {
		return address;
	}
	
	public Type getType() {
		return type;
	}
}
