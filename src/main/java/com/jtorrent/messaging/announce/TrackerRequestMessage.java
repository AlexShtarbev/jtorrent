package com.jtorrent.messaging.announce;

/**
 * An abstract class that defines the format of the request message to the tracker.
 * @see <a href="https://wiki.theory.org/BitTorrentSpecification#Tracker_Request_Parameters">Tracker Request Parameters</a>
 * 
 * @author Alex
 *
 */
public abstract class TrackerRequestMessage {
	public static final String INFO_HASH_KEY = "info_hash";
	public static final String PEER_ID_KEY = "peer_id";
	public static final String PORT_KEY = "port";
	public static final String UPLOADED_KEY = "uploaded";
	public static final String DOWNLOADED_KEY = "downloaded";
	public static final String LEFT_KEY = "left";
	public static final String COMPACT_KEY = "compact";
	public static final String NO_PEER_ID_KEY = "no_peer_id";
	public static final String EVENT_KEY = "event";
	public static final String IP_KEY = "ip";
	public static final String NUM_WANR_KEY = "numwant";
	public static final String KEY_KEY = "key";
	
	public static final int DEFAULT_NUM_WANT = 50;
	public static final int DEFAULT_COMPACT = 1;
	public static final int DEFAULT_NO_PEER_ID = 0;
	
	private final byte[] _infoHash;
	private final String _ip;
	private final int _port;
	private final String _peerID;
	private final long _uploaded;
	private final long _downloaded;
	private final long _left;
	/**
	 * Setting this to 1 indicates that the client accepts a compact response. 
	 * The peers list is replaced by a peers string with 6 bytes per peer. 
	 * The first four bytes are the host (in network byte order), the last two
	 *  bytes are the port (again in network byte order). It should be noted 
	 * that some trackers only support compact responses (for saving bandwidth)  
	 * and either refuse requests without "compact=1" or simply send a compact
	 * response unless the request contains "compact=0" (in which case they will
	 * refuse the request.)
	 */
	private final int _compact;
	/**
	 *  Indicates that the tracker can omit peer id field in peers dictionary. 
	 *  This option is ignored if compact is enabled.
	 */
	private final int _noPeerID;
	private final TrackerRequestEvent _requestEvent;
	private final int _numWant;
	private final int _key;
	
	public TrackerRequestMessage(byte[] infoHash, String ip,
			int port, String peerID, long uploaded, long downloaded, long left, int compact,
			int noPeerID, TrackerRequestEvent event, int numWant, int key) {
		_infoHash = infoHash;
		_ip = ip;
		_port = port;
		_peerID = peerID;
		_uploaded = uploaded;
		_downloaded = downloaded;
		_left = left;
		_compact = compact;
		_noPeerID = noPeerID;
		_requestEvent = event;
		_numWant = numWant;		
		_key = key;
	}

	public byte[] getInfoHash() {
		return _infoHash;
	}

	public String getIP() {
		return _ip;
	}
	
	public int getPort() {
		return _port;
	}

	public long getUploaded() {
		return _uploaded;
	}

	public long getDownloaded() {
		return _downloaded;
	}

	public long getLeft() {
		return _left;
	}

	public int getCompact() {
		return _compact;
	}

	public int getNoPeerID() {
		return _noPeerID;
	}

	public TrackerRequestEvent getRequestEvent() {
		return _requestEvent;
	}

	public int getNumWant() {
		return _numWant;
	}

	public int getKey() {
		return _key;
	}

	public String getPeerID() {
		return _peerID;
	}
	
}
