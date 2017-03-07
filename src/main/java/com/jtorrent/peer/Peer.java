package com.jtorrent.peer;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.channels.SocketChannel;

public class Peer {
	protected static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
	
	private InetSocketAddress _address;
	private String _peerID;
	private String _hexPerrID;
	private final String _host;
	
	public Peer(String host, int port) {
		this(host, port, null);
	}
	
	public Peer(String host, int port, String peerID) {
		setAddress(new InetSocketAddress(host, port));
		setPeerID(peerID);
		_host = _address.getAddress() + ":" + _address.getPort();
	}
	
	public Peer(Socket socket, String peerID) {
		this(
			socket.getInetAddress().getHostAddress(),
			socket.getPort(),
			peerID);
	}

	public InetSocketAddress getAddress() {
		return _address;
	}

	public void setAddress(InetSocketAddress address) {
		_address = address;
	}

	public String getPeerID() {
		return _peerID;
	}

	public void setPeerID(String peerID) {
		_peerID = peerID;
		if(peerID != null) {
			_hexPerrID = convertToHex(_peerID.getBytes());
		}
	}
	
	public String getIP() {
		return _address.getAddress().getHostAddress();
	}
	
	public String getHexPeerID() {
		return _hexPerrID;
	}
	
	/**
	 * Converts a byte array to a hexadecimal string representation of the data.
	 * @param bytes The data to convert
	 * @return A hexadecimal representation of the data.
	 */
	public static String convertToHex(byte[] bytes) {
	    char[] hexChars = new char[bytes.length * 2];
	    for ( int j = 0; j < bytes.length; j++ ) {
	        int v = bytes[j] & 0xFF;
	        hexChars[j * 2] = HEX_ARRAY[v >>> 4];
	        hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
	    }
	    return new String(hexChars);
	}
	
	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append(_address.getAddress().getHostAddress());
		sb.append(":" + _address.getPort());
		sb.append(" " + _peerID);
		
		return sb.toString();
	}

	public String getHostAddress() {
		return _host;
	}
	
	public void bind(SocketChannel socketChannel) throws SocketException {
		release();
		//TODO - implement - equivalent of SharingPeer.bind()
	}
	
	public void release() {
		// TODO - implement - equivalent of SharingPeer.unbind()
	}
	
	public boolean isConnected() {
		// TODO - implement - equivalent of SharingPeer.isConnected()
		return false;
	}
}
