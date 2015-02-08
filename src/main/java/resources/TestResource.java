package resources;

import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URL;
import java.net.URLConnection;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.codahale.metrics.annotation.Timed;

@Path("/test")
@Produces(MediaType.APPLICATION_JSON)
public final class TestResource {
  /**
   *  Default TOR Proxy port.
   */
  static int proxyPort = 9051;
  /**
   *  Default TOR Proxy hostaddr.
   */
  static String proxyAddr = "localhost";
  /**
   * Constant tells SOCKS4/4a to connect. Use it in the <i>req</i> parameter.
   */
  final static byte TOR_CONNECT = (byte) 0x01;
  /**
   * Constant tells TOR to do a DNS resolve.  Use it in the <i>req</i> parameter.
   */
  final static byte TOR_RESOLVE = (byte) 0xF0;
  /**
   * Constant indicates what SOCKS version are talking
   * Either SOCKS4 or SOCKS4a
   */
  final static byte SOCKS_VERSION = (byte) 0x04;
  /**
   * SOCKS uses Nulls as field delimiters 
   */
  final static byte SOCKS_DELIM = (byte) 0x00;
  /**
   * Setting the IP field to 0.0.0.1 causes SOCKS4a to
   * be enabled.
   */
  final static int SOCKS4A_FAKEIP = (int) 0x01;
  
  @GET
  @Timed
  public String sayHello() throws IOException {
    Socket torSocket = TorSocket("fncuwbiisyh6ak3i.onion", 80);
    
    DataInputStream is = new DataInputStream(torSocket.getInputStream());   
    PrintStream out = new java.io.PrintStream(torSocket.getOutputStream());
  
    //Construct an HTTP request
    out.print("GET / HTTP/1.0\r\n");
    out.print("Host: http://fncuwbiisyh6ak3i.onion:80\r\n");
    out.print("Accept: */*\r\n");
    out.print("Connection: Keep-Alive\r\n");
    out.print("Pragma: no-cache\r\n");
    out.print("\r\n");
    out.flush();

      // this is from Java Examples In a Nutshell
    final InputStreamReader from_server = new InputStreamReader(is);
    char[] buffer=new char[1024];
    int chars_read;
  
    // read until stream closes
    while((chars_read = from_server.read(buffer)) != -1) {
      // loop through array of chars
      // change \n to local platform terminator
      // this is a nieve implementation
      for(int j=0; j<chars_read; j++) {
        if(buffer[j]=='\n') System.out.println();
        else System.out.print(buffer[j]);
      }
      System.out.flush();       
    }
    torSocket.close();
    return "SUCCESS";
  }
  
  static Socket TorSocket(String targetHostname, int targetPort) 
  throws IOException {
    Socket s = TorSocketPre(targetHostname,targetPort, TOR_CONNECT);
    DataInputStream is = new DataInputStream(s.getInputStream());

    // only the status is useful on a TOR CONNECT
    byte version = is.readByte();
    byte status = is.readByte();
    if(status != (byte)90) {    
      //failed for some reason, return useful exception
      throw(new IOException(ParseSOCKSStatus(status)));
    }
//    System.out.println("status: "+ParseSOCKSStatus(status));
    int port = is.readShort();
    int ipAddr = is.readInt();
    return(s);
  }
  
  /**
   *  This method Creates a socket, then sends the inital SOCKS request info
   *  It stops before reading so that other methods may
   *  differently interpret the results.  It returns the open socket.
   *
   * @param targetHostname The hostname of the destination host.
   * @param targetPort The port to connect to
   * @param req SOCKS/TOR request code
   * @return An open Socket that has been sent the SOCK4a init codes.
   * @throws IOException from any Socket problems
   */
  static Socket TorSocketPre(String targetHostname, int targetPort, byte req) 
  throws IOException {
  
    Socket s;
    System.out.println("Opening connection to "+targetHostname+":"+targetPort+
        " via proxy "+proxyAddr+":"+proxyPort+" of type "+req);
    s = new Socket(proxyAddr, proxyPort);
    DataOutputStream os = new DataOutputStream(s.getOutputStream());
    os.writeByte(SOCKS_VERSION);
    os.writeByte(req);
    // 2 bytes 
    os.writeShort(targetPort);
    // 4 bytes, high byte first
    os.writeInt(SOCKS4A_FAKEIP);
    os.writeByte(SOCKS_DELIM);
    os.writeBytes(targetHostname);
    os.writeByte(SOCKS_DELIM);
    return(s);
  }

  /**
   * This helper method allows us to decode the SOCKS4 status codes into
   * Human readible input.<br />
   * Based upon info from http://archive.socks.permeo.com/protocol/socks4.protocol
   * @param status Byte containing the status code.
   * @return String human-readible representation of the error.
   */
  static String ParseSOCKSStatus(byte status) {
    // func to turn the status codes into useful output
    // reference 
    String retval;
    switch(status) {
    case 90:  
      retval = status+" Request granted.";
      break;
    case 91:
      retval = status+" Request rejected/failed - unknown reason.";
      break;
    case 92:
      retval = status+" Request rejected: SOCKS server cannot connect to identd on the client.";
      break;
    case 93:
      retval = status+" Request rejected: the client program and identd report different user-ids.";
      break;
    default:
      retval = status+" Unknown SOCKS status code.";                  
    }
    return(retval);
    
  }
}
