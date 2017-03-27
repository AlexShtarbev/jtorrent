package com.jtorrent.torrent;

import java.util.EventListener;

public interface TorrentSessionEventListener extends EventListener{
	
	public void onDownloadComplete();
}
