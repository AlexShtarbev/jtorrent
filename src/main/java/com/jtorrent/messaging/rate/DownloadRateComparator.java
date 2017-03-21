package com.jtorrent.messaging.rate;

import java.util.Comparator;

import com.jtorrent.peer.Peer;

public class DownloadRateComparator implements Comparator<Peer>{

	@Override
	public int compare(Peer peer1, Peer peer2) {
		return peer1.getDownloadRate().compareTo(peer2.getDownloadRate());
	}

}
