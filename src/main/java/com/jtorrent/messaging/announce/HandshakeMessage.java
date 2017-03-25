package com.jtorrent.messaging.announce;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jtorrent.torrent.TorrentSession;

/**
 * A wrapper for the handshake message that peers exchange before they start
 * sending each other messages.
 * 
 * @see <a href=
 *      "https://wiki.theory.org/BitTorrentSpecification#Handshake">Handshake</a>
 * @author Alex
 *
 */
public class HandshakeMessage {
	
	private static final Logger _logger = LoggerFactory.getLogger(HandshakeMessage.class);
	
	public static final String IDENTIFIER = "BitTorrent protocol";
	public static final int HANDSHAKE_LENGTH = 49;

	private final byte[] _infoHash;
	private final String _peerID;

	public HandshakeMessage(byte[] infoHash, String peerID) {
		_infoHash = infoHash;
		_peerID = peerID;
	}

	public static HandshakeMessage parse(SocketChannel channel) throws HandshakeException, IOException {
		ByteBuffer buff = ByteBuffer.allocate(HANDSHAKE_LENGTH + IDENTIFIER.length());
		channel.read(buff);
		buff.rewind();
		return parse(buff);
	}
	
	public static HandshakeMessage parse(ByteBuffer buff) throws HandshakeException, UnsupportedEncodingException {
		// Get the pstrlen first.
		int pstrlen = Byte.valueOf(buff.get()).intValue();
		// The handshake is a required message and must be the first message
		// transmitted
		// by the client. It is (49+len(pstr)) bytes long. Check if the remainig
		// data
		// in the buffer has that length.
		if (pstrlen < 0 || buff.remaining() != HANDSHAKE_LENGTH + pstrlen - 1) {
			throw new HandshakeException("invlaid length of " + pstrlen);
		}
		// Get the remaining bytes from the buffer.
		// Get the pstr - the identifier of the protocol.
		byte[] pstr = new byte[pstrlen];
		buff.get(pstr);
		String identifier = new String(pstr, TorrentSession.BYTE_ENCODING);
		if (!IDENTIFIER.equals(identifier)) {
			throw new HandshakeException("unexpected protocol identifier " + identifier);
		}

		// Skip the reserved bits as they are not used.
		buff.get(new byte[8]);

		byte[] infoHash = new byte[20];
		buff.get(infoHash);
		byte[] peerIDBytes = new byte[20];
		buff.get(peerIDBytes);
		String peerID = new String(peerIDBytes, TorrentSession.BYTE_ENCODING);

		return new HandshakeMessage(infoHash, peerID);
	}

	public static ByteBuffer make(byte[] infoHash, String peerID) {
		try {
			// The handshake is (49 + length(pstr)) bytes long, where pstr is
			// the
			// string identifier of the protocol.
			ByteBuffer buff = ByteBuffer.allocate(HANDSHAKE_LENGTH + IDENTIFIER.length());
			
			buff.put((byte) IDENTIFIER.length()); // pstrlen
			
			buff.put(IDENTIFIER.getBytes(TorrentSession.BYTE_ENCODING)); // pstr
						
			buff.put(new byte[8]); // reserved
			
			ByteBuffer infoHashBytes = ByteBuffer.wrap(infoHash);			
			buff.put(infoHashBytes); // info_hash
			
			ByteBuffer peerIDBytes = ByteBuffer.wrap(peerID.getBytes(TorrentSession.BYTE_ENCODING));
			buff.put(peerIDBytes); // peer_id
			
			buff.rewind();
			
			_logger.trace("sending hadnshake to peer {}: {} ", peerID, new String(buff.array(), Charset.forName(TorrentSession.BYTE_ENCODING)));
			return buff;
		} catch (UnsupportedEncodingException e) {
			return null;
		}
	}

	public static HandshakeMessage validate(TorrentSession session, SocketChannel channel, String peerID)
			throws HandshakeException {
		ByteBuffer buff = ByteBuffer.allocate(HANDSHAKE_LENGTH + IDENTIFIER.length());
		try {
			channel.read(buff);
			buff.rewind();
			InetAddress address = channel.socket().getInetAddress();
			HandshakeMessage handshake = parse(buff);
			if (check(session.getMetaInfo().getInfoHash(), handshake, peerID, address)) {
				return handshake;
			}
			
			return null;
		} catch (IOException e) {
			throw new HandshakeException(
					"could not read from the channel to " + channel.socket().getInetAddress().toString());
		}
	}
	
	public static boolean check(byte[] infoHash, HandshakeMessage handshake, String peerID,
			InetAddress peerInetAddress) throws HandshakeException, UnsupportedEncodingException {
		if (!Arrays.equals(handshake.getInfoHash(), infoHash)) {
			throw new HandshakeException(
					"info has does not match from channel" + peerInetAddress.toString());
		}

		if (peerID != null) {
			if (!peerID.equals(handshake.getPeerID())) {
				throw new HandshakeException(
						"peer ID not matching for channel " + peerInetAddress.toString());
			}
		}

		return true;
	}

	public String getPeerID() {
		return _peerID;
	}

	public byte[] getInfoHash() {
		return _infoHash;
	}
}