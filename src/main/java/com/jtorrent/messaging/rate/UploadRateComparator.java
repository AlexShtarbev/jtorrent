package com.jtorrent.messaging.rate;

import java.util.Comparator;

import com.jtorrent.peer.Peer;

public class UploadRateComparator implements Comparator<Peer>{

	@Override
	public int compare(Peer o1, Peer o2) {
		return o1.getDownloadRate().compareTo(o2.getDownloadRate());
	}

}
