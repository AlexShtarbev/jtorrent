package com.jtorrent.torrent.restore;

public class TorrentSessionRestorePoint {

	// The file location of the .torrent file.
	private String _torrentFile;
	// The path to the folder where the torrent is to be downloaded.
	private String _destinationFolder;
	
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
	
	@Override
	public String toString() {
		return _torrentFile + " in " + _destinationFolder;
	}
}
