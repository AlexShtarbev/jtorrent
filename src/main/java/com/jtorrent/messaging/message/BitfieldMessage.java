package com.jtorrent.messaging.message;

import java.nio.ByteBuffer;
import java.util.BitSet;

import com.jtorrent.torrent.TorrentSession;
import com.jtorrent.utils.Utils;

public class BitfieldMessage extends Message {

	private final BitSet _bitField;

	public BitfieldMessage(ByteBuffer payload) {
		super(MessageType.BITFIELD, payload);
		_bitField = Utils.convertByteBufferToBitSet(payload.duplicate());
	}

	public static boolean check(Message msg, TorrentSession torrentSession) {
		BitfieldMessage bmsg = (BitfieldMessage) msg;
		return bmsg.getLengthPrefix() < torrentSession.getPieceRepository().size();
	}

	public static BitfieldMessage parse(TorrentSession torrentSession, ByteBuffer data)
			throws MessageExchangeException {
		BitfieldMessage msg = new BitfieldMessage(data);
		if (!check(msg, torrentSession)) {
			throw new MessageExchangeException("invlaid bitfield");
		}
		return msg;
	}

	public static ByteBuffer make(BitSet bitSet, int numPieces) {
		ByteBuffer bitField = Utils.convertBitSetToByteBuffer(bitSet, numPieces);
		ByteBuffer message = ByteBuffer.allocateDirect(LENGTH_FIELD_SIZE + 1 + bitField.remaining());
		message.putInt(1 + bitField.remaining()); // len + bit field size
		message.put((byte)MessageType.BITFIELD.getMessageID());
		message.put(bitField);

		return message;
	}

	public BitSet getBitField() {
		return _bitField;
	}
}
