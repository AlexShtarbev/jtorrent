package com.jtorrent.messaging.message;

import java.nio.ByteBuffer;

import com.jtorrent.storage.PieceRepository;
import com.jtorrent.torrent.TorrentSession;

public class PieceMessage extends Message {

	private final int _pieceIndex;
	private final int _begin;
	private final ByteBuffer _block;

	public PieceMessage(ByteBuffer payload) {
		super(MessageType.PIECE, payload);

		_pieceIndex = payload.getInt();
		_begin = payload.getInt();
		_block = payload.slice();

		payload.rewind();
	}

	public static boolean check(Message msg, TorrentSession torrentSession) {
		PieceMessage req = (PieceMessage) msg;
		// Make sure that the piece's index is in bounds and that it does not go
		// over the
		// file storage limit.
		int index = req.getPieceIndex();
		PieceRepository repo = torrentSession.getPieceRepository();
		return index >= 0 && index < repo.size()
				&& req.getBegin() + req.getBlock().limit() <= repo.get(index).getSize();
	}

	public static PieceMessage parse(TorrentSession torrentSession, ByteBuffer data) throws MessageExchangeException {
		PieceMessage msg = new PieceMessage(data);
		if (!check(msg, torrentSession)) {
			throw new MessageExchangeException("Invalid piece message for piece #" + msg.getPieceIndex());
		}
		return msg;
	}

	public static ByteBuffer make(int pieceIndex, int begin, ByteBuffer block) {
		// piece: <len=0009+X><id=7><index><begin><block>
		ByteBuffer message = ByteBuffer.allocateDirect(LENGTH_FIELD_SIZE + 9 + block.capacity());
		message.putInt(9 + block.capacity());
		message.put((byte)MessageType.PIECE.getMessageID());
		message.putInt(pieceIndex);
		message.putInt(begin);
		message.put(block);
		return message;
	}

	public int getPieceIndex() {
		return _pieceIndex;
	}

	public int getBegin() {
		return _begin;
	}

	public ByteBuffer getBlock() {
		return _block;
	}

}
