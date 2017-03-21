package com.jtorrent.messaging.message;

import java.nio.ByteBuffer;

import com.jtorrent.messaging.message.Message.MessageType;
import com.jtorrent.torrent.TorrentSession;

public class Messages {

	public static Message parse(TorrentSession session, ByteBuffer message) throws MessageExchangeException {
		message.rewind();
		int len = message.getInt();
		if(len == 0) {
			return KeepAliveMessage.parse(session, message);
		}
		
		if(len != message.remaining()) {
			throw new IllegalArgumentException("expected message length of " + len + ", got"
					+ message.remaining());
		}
		
		// The next byte of the message contains the <id>.
		// Determine the message type by the id.
		int id = message.get() & 0xFF;
		MessageType messageType = MessageType.find(id);
		if(messageType == null) {
			throw new IllegalArgumentException("Unknown message ID.");
		}
		
		return parseMessage(session, message, messageType);		
	}
	
	private static Message parseMessage(TorrentSession session, ByteBuffer message, MessageType messageType) throws MessageExchangeException {
		ByteBuffer payload = message.slice();
		switch(messageType) {
		case CHOKE:
			return ChokeMessage.parse(session, payload);
		case UNCHOKE:
			return UnchokeMessage.parse(session, payload);
		case INTERESTED:
			return InterestedMessage.parse(session, payload);
		case NOT_INTERESTED:
			return NotInterestedMessage.parse(session, payload);
		case HAVE:
			return HaveMessage.parse(session, payload);
		case BITFIELD:
			return BitfieldMessage.parse(session, payload);
		case REQUEST:
			return RequestMessage.parse(session, payload);
		case PIECE:
			return PieceMessage.parse(session, payload);
		case CANCEL:
			return CancelMessage.parse(session, payload);
		default:
			throw new MessageExchangeException("Unproperly formatted message");
		}
	}
}
