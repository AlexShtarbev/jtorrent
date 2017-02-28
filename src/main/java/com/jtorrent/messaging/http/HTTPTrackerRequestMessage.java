package com.jtorrent.messaging.http;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;

import com.jtorrent.announce.messaging.TrackerRequestEvent;
import com.jtorrent.announce.messaging.TrackerRequestMessage;
import com.jtorrent.torrent.TorrentSession;

public class HTTPTrackerRequestMessage extends TrackerRequestMessage {
	

	public HTTPTrackerRequestMessage(byte[] infoHash, String ip, int port, String peerID,
			long uploaded, long downloaded, long left, int compact, int noPeerID, TrackerRequestEvent event,
			int numWant, int key) {
		super(infoHash, ip, port, peerID, uploaded, downloaded, left, compact, noPeerID, event, numWant, key);
	}

	public URL formTrackerRequest(URL trackerURL) throws UnsupportedEncodingException, MalformedURLException {
		StringBuilder url = new StringBuilder();
		// First add the URL to tracker itself;
		url.append(trackerURL.toString());
		// Then add parameters to the end of the URL.
		url.append(url.toString().contains("?") ? "&" : "?");
		url.append(INFO_HASH_KEY + "=").append(URLEncoder.encode(
				new String(this.getInfoHash(), TorrentSession.BYTE_ENCODING),
				TorrentSession.BYTE_ENCODING));
		url.append("&" + PEER_ID_KEY + "=").append(URLEncoder.encode(getPeerID(),
				TorrentSession.BYTE_ENCODING));
		url.append("&" + PORT_KEY + "=").append(getPort());
		url.append("&" + UPLOADED_KEY + "=").append(getUploaded());
		url.append("&" + DOWNLOADED_KEY + "=").append(getDownloaded());
		url.append("&" + LEFT_KEY + "=").append(getLeft());
		url.append("&" + COMPACT_KEY + "=").append(getCompact());
		url.append("&" + NO_PEER_ID_KEY + "=").append(getNoPeerID());
		
		if(getRequestEvent() != null && !TrackerRequestEvent.NONE.equals(getRequestEvent())) {
			url.append("&" + EVENT_KEY + "=").append(getRequestEvent().eventName());
		}
		
		if(getIP() != null) {
			url.append("&" + IP_KEY + "=").append(getIP());			
		}
		
		// FIXME
		//System.out.println(url.toString());
		return new URL(url.toString());
	}
}
