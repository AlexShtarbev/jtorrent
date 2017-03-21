package com.jtorrent.messaging.message;

import java.nio.ByteBuffer;

import com.jtorrent.storage.PieceRepository;
import com.jtorrent.torrent.TorrentSession;

public class RequestMessage extends Message {

	/** Default block size is 2^14 bytes, or 16kB. */
	public static final int DEFAULT_REQUEST_SIZE = 16384;

	/** Max block request size is 2^17 bytes, or 131kB. */
	public static final int MAX_REQUEST_SIZE = 131072;

	private final int _pieceIndex;
	private final int _begin;
	private final int _length;

	public RequestMessage(ByteBuffer payload) {
		super(MessageType.REQUEST, payload);

		_pieceIndex = payload.getInt();
		_begin = payload.getInt();
		_length = payload.getInt();

		payload.rewind();
	}

	public static boolean check(Message msg, TorrentSession torrentSession) {
		RequestMessage req = (RequestMessage) msg;
		// Make sure that the piece's index is in bounds and that it does not go
		// over the
		// file storage limit.
		int index = req.getPieceIndex();
		PieceRepository repo = torrentSession.getPieceRepository();
		return index >= 0 && index < repo.size() && req.getBegin() + req.getLength() <= repo.get(index).getSize();
	}

	public static RequestMessage parse(TorrentSession torrentSession, ByteBuffer data) throws MessageExchangeException {
		RequestMessage msg = new RequestMessage(data);
		if (!check(msg, torrentSession)) {
			throw new MessageExchangeException("Invalid request message for piece #" + msg.getPieceIndex());
		}
		return msg;
	}

	public static ByteBuffer make(int pieceIndex, int begin, int length) {
		// request: <len=0013><id=6><index><begin><length>
		ByteBuffer message = ByteBuffer.allocateDirect(LENGTH_FIELD_SIZE + 13);
		message.putInt(13);
		message.put((byte)MessageType.REQUEST.getMessageID());
		message.putInt(pieceIndex);
		message.putInt(begin);
		message.putInt(length);
		return message;
	}

	public int getLength() {
		return _length;
	}

	public int getBegin() {
		return _begin;
	}

	public int getPieceIndex() {
		return _pieceIndex;
	}

}
