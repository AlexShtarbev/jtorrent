package com.jtorrent.torrent;

import com.jtorrent.peer.Peer;

public class SessionInfo {
	private long _downloaded;
	private long _uploaded;
	private long _left;
	/**
	 * The torrent client is also a peer.
	 */
	private final Peer _clientPeer;
	
	public SessionInfo(Peer clientPeer) {
		_clientPeer = clientPeer;
	}

	public long getDownloaded() {
		return _downloaded;
	}

	public void setDownloaded(long downloaded) {
		_downloaded = downloaded;
	}

	public long getUploaded() {
		return _uploaded;
	}

	public void setUploaded(long uploaded) {
		_uploaded = uploaded;
	}

	public long getLeft() {
		return _left;
	}

	public void setLeft(long left) {
		_left = left;
	}

	public Peer getClientPeer() {
		return _clientPeer;
	}
	
}
