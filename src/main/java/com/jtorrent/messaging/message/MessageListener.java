package com.jtorrent.messaging.message;

import java.nio.ByteBuffer;

public interface MessageListener {
	
	public void onMessageReceived(ByteBuffer msg);
	
	public void onMessageChannelException(Exception e);
}
