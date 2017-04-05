package com.jtorrent.torrent.restore;

public class TorrentSessionRestorePoint {

	// The file location of the .torrent file.
	private String _torrentFile;
	// The path to the folder where the torrent is to be downloaded.
	private String _destinationFolder;
	// The path to the folder where the torrent is to be downloaded.
	private boolean _stopped;
	
	public void setTorrentFile(String filePath) {
		_torrentFile = filePath;
	}
	
	public String getTorrentFile() {
		return _torrentFile;
	}
	
	public void setDestinationFolder(String filePath) {
		_destinationFolder = filePath;
	}
	
	public String getDestinationFolder() {
		return _destinationFolder;
	}
	
	public void setStopped(boolean stopped) {
		_stopped = stopped;
	}
	
	public boolean getStopped() {
		return _stopped;
	}
	
	@Override
	public String toString() {
		return _torrentFile + " in " + _destinationFolder + " stopped: " + _stopped;
	}
}
