package com.jtorrent.messaging.message;

import java.nio.ByteBuffer;

import com.jtorrent.torrent.TorrentSession;

public class HaveMessage extends Message {
	private final int _pieceIndex;

	private HaveMessage(ByteBuffer payload) {
		super(MessageType.HAVE, payload);
		_pieceIndex = payload.duplicate().getInt();
	}

	public static boolean check(Message msg, TorrentSession torrentSession) {
		HaveMessage hmsg = (HaveMessage) msg;
		return hmsg.getPieceIndex() >= 0 && hmsg.getPieceIndex() < torrentSession.getPieceRepository().size();
	}

	public static HaveMessage parse(TorrentSession torrentSession, ByteBuffer data) throws MessageExchangeException {
		HaveMessage msg = new HaveMessage(data);
		if (!check(msg, torrentSession)) {
			throw new MessageExchangeException("invlaid piece index" + msg.getPieceIndex());
		}
		return msg;
	}

	public static ByteBuffer make(int pieceIndex) {
		// have: <len=0005><id=4><piece index>
		ByteBuffer message = ByteBuffer.allocateDirect(LENGTH_FIELD_SIZE + 5);
		message.putInt(5);
		message.put((byte)MessageType.HAVE.getMessageID());
		message.putInt(pieceIndex);
		return message;
	}

	public int getPieceIndex() {
		return _pieceIndex;
	}

	@Override
	public String toString() {
		return "have piece: " + _pieceIndex;
	}
}
