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

	public synchronized long getDownloaded() {
		return _downloaded;
	}

	public synchronized void setDownloaded(long downloaded) {
		_downloaded = downloaded;
	}

	public synchronized long getUploaded() {
		return _uploaded;
	}

	public synchronized void setUploaded(long uploaded) {
		_uploaded = uploaded;
	}

	public synchronized long getLeft() {
		return _left;
	}

	public synchronized void setLeft(long left) {
		_left = left;
	}

	public Peer getClientPeer() {
		return _clientPeer;
	}

}
