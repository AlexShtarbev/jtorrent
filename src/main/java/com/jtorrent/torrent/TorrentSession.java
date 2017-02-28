package com.jtorrent.torrent;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;

import com.jtorrent.announce.AnnounceService;
import com.jtorrent.metainfo.MetaInfo;
import com.jtorrent.peer.Peer;

public class TorrentSession {
	public static final String BYTE_ENCODING = "ISO-8859-1";
	
	private final MetaInfo _metaInfo;
	private final SessionInfo _sessionInfo;
	private final AnnounceService _announceService;
	
	public TorrentSession(String torrentFileName, String destionation, Peer clientPeer) throws NoSuchAlgorithmException, InvalidAlgorithmParameterException, IOException, URISyntaxException {
		_metaInfo = new MetaInfo(new File(torrentFileName));
		_sessionInfo = new SessionInfo(clientPeer);
		_announceService = new AnnounceService(this);
	}
	
	public void startSession() {
		_announceService.start();
	}
	
	public void stopSession() {
		try {
			_announceService.stop(false);
		} catch (InterruptedException e) {
			// Ignore.
		}
	}
	
	/**
	 * 
	 * @return The metainfo information from the .torrent file.
	 */
	public MetaInfo getMetaInfo() {
		return _metaInfo;
	}

	public SessionInfo getSessionInfo() {
		return _sessionInfo;
	}
}
