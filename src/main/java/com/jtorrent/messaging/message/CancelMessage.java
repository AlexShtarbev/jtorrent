package com.jtorrent.messaging.message;

import java.nio.ByteBuffer;

import com.jtorrent.storage.PieceRepository;
import com.jtorrent.torrent.TorrentSession;

public class CancelMessage extends Message {

	private final int _pieceIndex;
	private final int _begin;
	private final int _length;

	public CancelMessage(ByteBuffer payload) {
		super(MessageType.CANCEL, payload);

		_pieceIndex = payload.getInt();
		_begin = payload.getInt();
		_length = payload.getInt();

		payload.rewind();
	}

	public static boolean check(Message msg, TorrentSession torrentSession) {
		CancelMessage req = (CancelMessage) msg;
		// Make sure that the piece's index is in bounds and that it does not go
		// over the
		// file storage limit.
		int index = req.getPieceIndex();
		PieceRepository repo = torrentSession.getPieceRepository();
		return index < 0 || index > repo.size() || req.getBegin() + req.getLength() > repo.get(index).getSize();
	}

	public static CancelMessage parse(TorrentSession torrentSession, ByteBuffer data) throws MessageExchangeException {
		CancelMessage msg = new CancelMessage(data);
		if (!check(msg, torrentSession)) {
			throw new MessageExchangeException("Invalid piece message for piece #" + msg.getPieceIndex());
		}
		return msg;
	}

	public static ByteBuffer make(int pieceIndex, int begin, int length) {
		// cancel: <len=0013><id=8><index><begin><length>
		ByteBuffer message = ByteBuffer.allocateDirect(LENGTH_FIELD_SIZE + 13);
		message.putInt(13);
		message.put((byte)MessageType.CANCEL.getMessageID());
		message.putInt(pieceIndex);
		message.putInt(begin);
		message.putInt(length);
		return message;
	}

	public int getPieceIndex() {
		return _pieceIndex;
	}

	public int getBegin() {
		return _begin;
	}

	public int getLength() {
		return _length;
	}

}
