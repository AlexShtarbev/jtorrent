package com.jtorrent.announce;

import java.net.URI;

import com.jtorrent.announce.messaging.TrackerRequestEvent;
import com.jtorrent.torrent.TorrentSession;

public class UDPTrackerClient extends TrackerClient {

	public UDPTrackerClient(TorrentSession session, URI trackerURI) {
		super(session, trackerURI);
		// TODO Auto-generated constructor stub
	}

	@Override
	public void queryTracker(TrackerRequestEvent event) {
		// TODO Auto-generated method stub
		
	}

}
