package com.jtorrent.messaging.message;

import java.nio.ByteBuffer;

import com.jtorrent.torrent.TorrentSession;

/**
 * Choke message: <code>&lt;len=0001&gt;;&lt;id=1&gt;</code>
 * 
 * @see <a href=
 *      "https://wiki.theory.org/BitTorrentSpecification#unchoke:_.3Clen.3D0001.3E.3Cid.3D1.3E">Unchoke
 *      message</a>
 */
public class UnchokeMessage extends Message {

	public UnchokeMessage(ByteBuffer payload) {
		super(MessageType.UNCHOKE, payload);

	}

	public static UnchokeMessage parse(TorrentSession torrentSession, ByteBuffer data) {
		return new UnchokeMessage(data);
	}

	public static ByteBuffer make() {
		return idPayload(MessageType.UNCHOKE.getMessageID());
	}

}
