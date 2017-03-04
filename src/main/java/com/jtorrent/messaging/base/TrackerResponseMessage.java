package com.jtorrent.messaging.base;

import java.util.List;

import com.jtorrent.peer.Peer;

public abstract class TrackerResponseMessage {
	public static final String INTERVAL_KEY = "interval";
	public static final String COMPLETE_KEY = "complete";
	public static final String INCOMPLETE_KEY = "incomplete";
	public static final String PEERS_KEY = "peers";
	public static final String FAILURE_REASON_KEY = "failure reason";
	public static final String WARNING_MESSAGE_KEY = "warning message";
	
	private final int _interval;
	private final int _complete; // seeder for UDP BEP #12
	private final int _incomplete; // leechers for UDP BEP #12
	private final List<Peer> _peers;

	private final String _failureReason;
	private final String _warningMessage;
	
	public TrackerResponseMessage(String failureReason, String warningMessage, int interval, int complete, int incomplete, List<Peer> peers) {
		_interval = interval;
		_complete = complete;
		_incomplete = incomplete;
		_peers = peers;
		_failureReason = failureReason;
		_warningMessage = warningMessage;
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
	
	public String getFailureReason() {
		return _failureReason;
	}

	public String getWarningMessage() {
		return _warningMessage;
	}
}
