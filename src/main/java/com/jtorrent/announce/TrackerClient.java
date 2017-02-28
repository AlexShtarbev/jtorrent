package com.jtorrent.announce;

import java.net.URI;

import com.jtorrent.announce.messaging.TrackerRequestEvent;
import com.jtorrent.torrent.TorrentSession;

public abstract class TrackerClient {
	protected TorrentSession _session;
	protected URI _trackerURI;

	public TrackerClient(TorrentSession session, URI trackerURI) {
		_session = session;
		_trackerURI = trackerURI;
	}
	
	public void close(){};
	
	public abstract void queryTracker(TrackerRequestEvent event) throws AnnounceException;
}
