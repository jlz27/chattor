package network;

import java.io.Serializable;

public final class TorAddress implements Serializable {
	private static final long serialVersionUID = 1L;

	private final String address;
	private final int port;
	
	public TorAddress(String address, int port) {
		this.address = address;
		this.port = port;
	}
	public String getAddress() {
		return address;
	}
	
	public int getPort() {
		return port;
	}
	
	@Override
	public String toString() {
		return "TorAddress [address=" + address + ", port=" + port + "]";
	}
}
