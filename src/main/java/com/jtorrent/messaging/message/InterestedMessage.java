package com.jtorrent.messaging.message;

import java.nio.ByteBuffer;

import com.jtorrent.torrent.TorrentSession;

/**
 * Choke message: <code>&lt;len=0001&gt;;&lt;id=2&gt;</code>
 * 
 * @see <a href=
 *      "https://wiki.theory.org/BitTorrentSpecification#interested:_.3Clen.3D0001.3E.3Cid.3D2.3E">Interested
 *      message</a>
 */
public class InterestedMessage extends Message {

	public InterestedMessage(ByteBuffer payload) {
		super(MessageType.INTERESTED, payload);
	}

	public static InterestedMessage parse(TorrentSession torrentSession, ByteBuffer data) {
		return new InterestedMessage(data);
	}

	public static ByteBuffer make() {
		return idPayload(MessageType.INTERESTED.getMessageID());
	}

}
