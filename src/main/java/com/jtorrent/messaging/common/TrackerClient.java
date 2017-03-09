package com.jtorrent.messaging.common;

import java.io.IOException;
import java.net.URI;

import com.jtorrent.messaging.announce.AnnounceException;
import com.jtorrent.messaging.announce.TrackerRequestEvent;
import com.jtorrent.messaging.announce.TrackerResponseMessage;
import com.jtorrent.torrent.TorrentSession;

public abstract class TrackerClient {
	protected TorrentSession _session;
	protected URI _trackerURI;

	public TrackerClient(TorrentSession session, URI trackerURI) {
		_session = session;
		_trackerURI = trackerURI;
	}
	
	public void close(){};
	
	public abstract TrackerResponseMessage queryTracker(TrackerRequestEvent event) throws AnnounceException, IOException;
}