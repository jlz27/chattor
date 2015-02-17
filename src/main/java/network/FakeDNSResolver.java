package network;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.http.conn.DnsResolver;

public class FakeDNSResolver implements DnsResolver {

	@Override
	public InetAddress[] resolve(String host) throws UnknownHostException {
		return new InetAddress[] { InetAddress.getByAddress(new byte[] { 1, 1, 1, 1 }) };
	}

}
