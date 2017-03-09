package com.jtorrent.messaging.udp;

import java.net.URI;

import com.jtorrent.messaging.announce.TrackerRequestEvent;
import com.jtorrent.messaging.announce.TrackerResponseMessage;
import com.jtorrent.messaging.common.TrackerClient;
import com.jtorrent.torrent.TorrentSession;

public class UDPTrackerClient extends TrackerClient {

	public UDPTrackerClient(TorrentSession session, URI trackerURI) {
		super(session, trackerURI);
		// TODO Auto-generated constructor stub
	}

	@Override
	public TrackerResponseMessage queryTracker(TrackerRequestEvent event) {
		// TODO Auto-generated method stub
		return null;
	}

}
