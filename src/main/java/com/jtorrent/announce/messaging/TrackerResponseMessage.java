package com.jtorrent.announce.messaging;

import java.util.List;

import com.jtorrent.peer.Peer;

public abstract class TrackerResponseMessage {
	public static final String INTERVAL_KEY = "interval";
	public static final String COMPLETE_KEY = "complete";
	public static final String INCOMPLETE_KEY = "incomplete";
	public static final String PEERS_KEY = "peers";
	
	private final int _interval;
	private final int _complete; // seeder for UDP BEP #12
	private final int _incomplete; // leechers for UDP BEP #12
	private final List<Peer> _peers;
	
	public TrackerResponseMessage(int interval, int complete, int incomplete, List<Peer> peers) {
		_interval = interval;
		_complete = complete;
		_incomplete = incomplete;
		_peers = peers;
	}

	public int getInterval() {
		return _interval;
	}

	public int getComplete() {
		return _complete;
	}

	public int getIncomplete() {
		return _incomplete;
	}

	public List<Peer> getPeers() {
		return _peers;
	}
}
