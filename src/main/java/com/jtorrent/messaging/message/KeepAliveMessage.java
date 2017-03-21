package com.jtorrent.messaging.message;

import java.nio.ByteBuffer;

import com.jtorrent.torrent.TorrentSession;

/**
 * Keep-alive message: <code>&lt;len=0000&gt;</code>
 * 
 * @see <a href=
 *      "https://wiki.theory.org/BitTorrentSpecification#keep-alive:_.3Clen.3D0000.3E">Keep
 *      alive message</a>
 */
public class KeepAliveMessage extends Message {

	public KeepAliveMessage(ByteBuffer payload) {
		super(MessageType.KEEP_ALIVE, payload);
	}

	public static KeepAliveMessage parse(TorrentSession torrentSession, ByteBuffer data) {
		return new KeepAliveMessage(data);
	}

	public static ByteBuffer make() {
		return idPayload(MessageType.KEEP_ALIVE.getMessageID());
	}

}
