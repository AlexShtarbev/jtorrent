package com.jtorrent.peer;

import java.net.InetSocketAddress;

import javax.xml.soap.SAAJResult;

public class Peer {
	private InetSocketAddress _address;
	private String _peerID;
	
	public Peer(String host, int port) {
		this(host, port, null);
	}
	
	public Peer(String host, int port, String peerID) {
		setAddress(new InetSocketAddress(host, port));
		setPeerID(peerID);
	}

	public InetSocketAddress getAddress() {
		return _address;
	}

	public void setAddress(InetSocketAddress _address) {
		this._address = _address;
	}

	public String getPeerID() {
		return _peerID;
	}

	public void setPeerID(String _peerID) {
		this._peerID = _peerID;
	}
	
	public String getIP() {
		return _address.getAddress().getHostAddress();
	}
	
	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append(_address.getAddress().getHostAddress());
		sb.append(":" + _address.getPort());
		sb.append(" " + _peerID);
		
		return sb.toString();
	}
}
