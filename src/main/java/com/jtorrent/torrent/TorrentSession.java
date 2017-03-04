package com.jtorrent.torrent;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;

import com.jtorrent.messaging.AnnounceService;
import com.jtorrent.messaging.base.TrackerResponseMessage;
import com.jtorrent.messaging.conn.ConnectionService;
import com.jtorrent.metainfo.MetaInfo;
import com.jtorrent.peer.Peer;
import com.jtorrent.peer.PeerManager;

public class TorrentSession {
	public static final String BYTE_ENCODING = "ISO-8859-1";
	
	private final MetaInfo _metaInfo;
	private final SessionInfo _sessionInfo;
	private final AnnounceService _announceService;
	private final PeerManager _peerManager;
	private final ConnectionService _connectionService;
	
	public TorrentSession(String torrentFileName, String destionation, Peer clientPeer, ConnectionService connectionService) throws NoSuchAlgorithmException, InvalidAlgorithmParameterException, IOException, URISyntaxException {
		_metaInfo = new MetaInfo(new File(torrentFileName));
		_sessionInfo = new SessionInfo(clientPeer);
		_announceService = new AnnounceService(this);
		_connectionService = connectionService;
		_peerManager = new PeerManager(connectionService, this);
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
	 * <p>
	 * 	Handles the periodic announce and update response to the tracker.
	 * </p>
	 * <p>
	 * 	<b>NOTE:</b> This is the response sent from the tracker when the
	 * 	client sends its START announce requests and then the following
	 * 	update messages and their responses.
	 * </p>
	 * @param message The response message from the tracker.
	 */
	public void handleTrackerResponse(TrackerResponseMessage message) {
		_peerManager.registerConnectionAll(message.getPeers());
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

	public PeerManager getPeerManager() {
		return _peerManager;
	}

	public ConnectionService getConnectionService() {
		return _connectionService;
	}
}
