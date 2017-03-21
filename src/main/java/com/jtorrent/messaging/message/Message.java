package com.jtorrent.messaging.message;

import java.nio.ByteBuffer;

import com.jtorrent.torrent.TorrentSession;

public class Message {

	/**
	 * The &lt;length&gt; field in each message is 4 bytes (32 bits long).
	 */
	public static final int LENGTH_FIELD_SIZE = 4;

	public static enum MessageType {
		KEEP_ALIVE(-1), CHOKE(0), UNCHOKE(1), INTERESTED(2), NOT_INTERESTED(3), HAVE(4), BITFIELD(5), REQUEST(
				6), PIECE(7), CANCEL(8);

		private int _messageID;

		private MessageType(int messageID) {
			_messageID = messageID;
		}

		public int getMessageID() {
			return _messageID;
		}

		public static MessageType find(int which) {
			for (MessageType message : MessageType.values()) {
				if (message.getMessageID() == which) {
					return message;
				}
			}

			return null;
		}
	}

	protected final long _lengthPrefix;
	protected final MessageType _messageType;
	protected final ByteBuffer _payload;

	public Message(MessageType messageType, ByteBuffer payload) {
		this(payload.remaining(), messageType, payload);
	}

	public Message(long lengthPrefix, MessageType messageType, ByteBuffer payload) {
		_lengthPrefix = lengthPrefix;
		_messageType = messageType;
		_payload = payload;
	}

	public long getLengthPrefix() {
		return _lengthPrefix;
	}

	public MessageType getMessageType() {
		return _messageType;
	}

	public ByteBuffer getPayload() {
		return _payload.duplicate();
	}

	protected static ByteBuffer idPayload(int id) {
		int payloadSize = LENGTH_FIELD_SIZE;
		if (id >= 0) {
			payloadSize++;
		}
		
		ByteBuffer payload = ByteBuffer.allocate(payloadSize);
		// Put the length
		int length = id > 0 ? 1 : 0;
		payload.putInt(length);
		
		// Put the id if it is not a keep-alice message.
		if (id >= 0) {
			payload.put((byte)id);
		}
		
		return payload;
	}

	/**
	 * Checks if the message is valid in the context of the torrent session.
	 * 
	 * @param msg
	 *            The message.
	 * @param torrentSession
	 *            The torrent session to which the message was sent.
	 * @return <b>true</b> - if the message is correct;<b>false</b> - the
	 *         message is not applicable in the context of the torrent session.
	 */
	public static boolean check(Message msg, TorrentSession torrentSession) {
		return true;
	}

	/**
	 * Parses the payload into the corresponding message.
	 * 
	 * @param torrentSession
	 *            The torrent session to which the message is sent.
	 * @param data
	 *            The payload data
	 * @return Message object
	 * @throws MessageExchangeException
	 */
	public static Message parse(TorrentSession torrentSession, ByteBuffer data) throws MessageExchangeException {
		return null;
	}

	/**
	 * Creates a Message object from the provided parameters.
	 * 
	 * @return
	 */
	public static ByteBuffer make() {
		return null;
	}

}
