package com.jtorrent.announce;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;

import org.apache.commons.io.output.ByteArrayOutputStream;

import com.jtorrent.announce.messaging.TrackerRequestEvent;
import com.jtorrent.announce.messaging.TrackerRequestMessage;
import com.jtorrent.messaging.http.HTTPTrackerRequestMessage;
import com.jtorrent.messaging.http.HTTPTrackerResponseMessage;
import com.jtorrent.peer.Peer;
import com.jtorrent.torrent.*;

public class HTTPTrackerClient extends TrackerClient{

	public HTTPTrackerClient(TorrentSession session, URI trackerURI) {
		super(session, trackerURI);
	}

	@Override
	public void queryTracker(TrackerRequestEvent event) throws AnnounceException {
		InputStream in = send(event);
		if(in == null){
			throw new AnnounceException("got no response from tracker");
		}
		
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			out.write(in);
			//FIXME
			System.out.println("received response from tracker " + _trackerURI);
			//System.out.println(_trackerURI.toString() + out.toString());
			ByteBuffer responseMessageBuffer = ByteBuffer.wrap(out.toByteArray());
			HTTPTrackerResponseMessage response = HTTPTrackerResponseMessage.parse(responseMessageBuffer);
			// FIXME
			System.out.println("printing response...");
			System.out.println(response.toString());
		} catch (IOException e) {
			throw new AnnounceException("could not write the input stream from the tracker");
		} finally {
			try {
				out.close();
			} catch (IOException e) {
				// TODO - log
			}
		}
	}
	
	private InputStream send(TrackerRequestEvent event) throws AnnounceException {
		Peer clientPeer = _session.getSessionInfo().getClientPeer();
		SessionInfo sessionInfo = _session.getSessionInfo();
		HTTPTrackerRequestMessage message = new HTTPTrackerRequestMessage(
				_session.getMetaInfo().getInfoHash(),
				clientPeer.getIP(), clientPeer.getAddress().getPort(),
				clientPeer.getPeerID(), sessionInfo.getUploaded(),
				sessionInfo.getDownloaded(), sessionInfo.getLeft(),
				1, 0, event, TrackerRequestMessage.DEFAULT_NUM_WANT, 0);
		try {
			URL getRequest = message.formTrackerRequest(_trackerURI.toURL());
			
			HttpURLConnection conn = (HttpURLConnection) getRequest.openConnection();
			if(conn == null){
				throw new AnnounceException("could not open connection to target traker");
			}
			
			return conn.getInputStream();
		} catch (UnsupportedEncodingException e) {
			throw new AnnounceException("could not bencode data");
		} catch (MalformedURLException e) {
			throw new AnnounceException("could not create am HTTP GET request from the parameters");
		} catch (IOException e) {
			throw new AnnounceException("could not build HTTP tracker request");
		}
	}

}
