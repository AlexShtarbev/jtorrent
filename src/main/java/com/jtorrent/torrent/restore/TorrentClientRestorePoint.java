package com.jtorrent.torrent.restore;

import java.util.List;

public class TorrentClientRestorePoint {
	private List<TorrentSessionRestorePoint> _torrentSessions;
	
	public void setTorrentSessions(List<TorrentSessionRestorePoint> sessions) {
		_torrentSessions = sessions;
	}
	
	public List<TorrentSessionRestorePoint> getTorrentSessions() {
		return _torrentSessions;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for(TorrentSessionRestorePoint rp : _torrentSessions) {
			sb.append(rp.toString() + "\n");
		}
		
		return sb.toString();
	}
}
