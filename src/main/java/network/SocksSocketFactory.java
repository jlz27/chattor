package network;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;

import org.apache.http.HttpHost;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.protocol.HttpContext;

public final class SocksSocketFactory extends PlainConnectionSocketFactory {

	private final InetSocketAddress socksAddr;
	
	public SocksSocketFactory(InetSocketAddress socksAddr) {
		this.socksAddr = socksAddr;
	}
	
	@Override
    public Socket createSocket(final HttpContext context) throws IOException {
		Proxy proxy = new Proxy(Proxy.Type.SOCKS, socksAddr);
        return new Socket(proxy);
    }
	
	@Override
    public Socket connectSocket(int connectTimeout, Socket socket, HttpHost host, InetSocketAddress remoteAddress,
            InetSocketAddress localAddress, HttpContext context) throws IOException {
        // Convert address to unresolved
        InetSocketAddress unresolvedRemote = InetSocketAddress
                .createUnresolved(host.getHostName(), remoteAddress.getPort());
        return super.connectSocket(connectTimeout, socket, host, unresolvedRemote, localAddress, context);
    }
}
