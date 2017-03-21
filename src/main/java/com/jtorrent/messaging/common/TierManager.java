package com.jtorrent.messaging.common;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.jtorrent.messaging.http.HTTPTrackerClient;
import com.jtorrent.messaging.udp.UDPTrackerClient;
import com.jtorrent.torrent.TorrentSession;

/**
 * Manages the announce-list tier algorithm described in BEP #12.
 * 
 * @see <a href="http://www.bittorrent.org/beps/bep_0012.html">Metadata
 *      Extension</a>
 * @author Alex
 *
 */
public class TierManager {

	private final List<List<TrackerClient>> _tierList;
	private final TorrentSession _session;

	private int _tierPosition;
	private int _clientPosition;

	public TierManager(TorrentSession session) {
		_session = session;
		_tierList = provideTierList(session.getMetaInfo().getAnnounceList());
	}

	private List<List<TrackerClient>> provideTierList(List<List<URI>> announceList) {
		// The announce list has been parsed from the metainfo file. Now a
		// connection
		// needs to be made between the torrent client and a tracker.

		// All the URIs are converted to TrackerClients and the client which
		// will be
		// queried is determined as described in BEP #12 - the tier list is
		// created
		// by shuffling every tier and adding it to a larger pool which contains
		// all
		// tier lists.
		List<List<TrackerClient>> tierList = new ArrayList<>();
		for (List<URI> tier : announceList) {
			List<TrackerClient> currentTierList = new ArrayList<TrackerClient>();
			for (URI trackerURI : tier) {
				TrackerClient trackerClient = provideTrackerClient(trackerURI);
				if (trackerClient == null) {
					throw new UnsupportedOperationException("cannot create a tracker client from URI: " + trackerURI);
				} else {
					currentTierList.add(trackerClient);
				}
			}
			// The current tier is shuffled and then added to the tier list.
			Collections.shuffle(currentTierList);
			tierList.add(currentTierList);
		}
		return tierList;
	}

	private TrackerClient provideTrackerClient(URI trackerURI) {
		String URIScheme = trackerURI.getScheme();

		TrackerClient trackerClient = null;
		if ("http".equals(URIScheme) || "https".equals(URIScheme)) {
			trackerClient = new HTTPTrackerClient(_session, trackerURI);
		} else if ("udp".equals(URIScheme)) {
			trackerClient = new UDPTrackerClient(_session, trackerURI);
		}

		return trackerClient;
	}

	public void tryNextTrackerClient() {
		_clientPosition++;
		if (_clientPosition >= _tierList.get(_tierPosition).size()) {
			_clientPosition = 0;
			_tierPosition++;

			if (_tierPosition >= _tierList.size()) {
				_tierPosition = 0;
			}
		}
	}

	public TrackerClient provideTrackerClient() {
		return _tierList.get(_tierPosition).get(_clientPosition);
	}

	/**
	 * Moves the last tracker, to which the client connected to successfully, to
	 * the front of the tier list as described in BEP #12.
	 */
	public void moveTrackerToFront() {
		Collections.swap(_tierList.get(_tierPosition), _clientPosition, 0);
	}

	public List<List<TrackerClient>> getTierList() {
		return _tierList;
	}

	public void closeAllConnections() {
		for (List<TrackerClient> tier : _tierList) {
			for (TrackerClient trackerClient : tier) {
				trackerClient.close();
			}
		}
	}
}
