package com.jtorrent.messaging.message;

import java.nio.ByteBuffer;

import com.jtorrent.messaging.message.Message.MessageType;
import com.jtorrent.torrent.TorrentSession;

/**
 * Choke message: <code>&lt;len=0001&gt;&lt;id=0&gt;</code>
 * 
 * @see <a href=
 *      "https://wiki.theory.org/BitTorrentSpecification#choke:_.3Clen.3D0001.3E.3Cid.3D0.3E">Choke
 *      message</a>
 */
public class ChokeMessage extends Message {

	public ChokeMessage(ByteBuffer payload) {
		super(MessageType.CHOKE, payload);
	}

	public static Message parse(TorrentSession torrentSession, ByteBuffer data) {
		return new ChokeMessage(data);
	}

	public static ByteBuffer make() {
		return idPayload(MessageType.CHOKE.getMessageID());
	}

}
