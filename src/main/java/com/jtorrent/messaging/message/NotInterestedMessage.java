package com.jtorrent.messaging.message;

import java.nio.ByteBuffer;
import com.jtorrent.torrent.TorrentSession;

/**
 * Choke message: <code>&lt;len=0001&gt;&lt;id=3&gt;</code>
 * 
 * @see <a href=
 *      "https://wiki.theory.org/BitTorrentSpecification#not_interested:_.3Clen.3D0001.3E.3Cid.3D3.3E">Not
 *      interested message</a>
 */
public class NotInterestedMessage extends Message {

	public NotInterestedMessage(ByteBuffer payload) {
		super(MessageType.NOT_INTERESTED, payload);
	}

	public static NotInterestedMessage parse(TorrentSession torrentSession, ByteBuffer data) {
		return new NotInterestedMessage(data);
	}

	public static ByteBuffer make() {
		return idPayload(MessageType.NOT_INTERESTED.getMessageID());
	}

}
