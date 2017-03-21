package com.jtorrent.messaging.message;

@SuppressWarnings("serial")
public class MessageExchangeException extends Exception {

	public MessageExchangeException(String arg) {
		super("Error exhanging execption: " + arg);
	}
}
