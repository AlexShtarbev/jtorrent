package com.jtorrent.messaging.announce;

public class ResponseException extends Exception {
	private static final long serialVersionUID = 1L;

	public ResponseException() {
		super();
	}

	public ResponseException(String s) {
		super(s);
	}
}
