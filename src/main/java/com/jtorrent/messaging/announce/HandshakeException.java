package com.jtorrent.messaging.announce;

public class HandshakeException extends Exception {
	private static final long serialVersionUID = 1L;

	public HandshakeException() {
		super();
	}
	
	public HandshakeException(String msg) {
		super(msg);
	}
}
