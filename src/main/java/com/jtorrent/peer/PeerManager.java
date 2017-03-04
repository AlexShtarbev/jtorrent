package com.jtorrent.peer;

import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import com.jtorrent.messaging.base.TrackerResponseMessage;
import com.jtorrent.messaging.conn.ConnectionService;
import com.jtorrent.messaging.conn.ConnectionService.HandshakeResponse;
import com.jtorrent.messaging.conn.HandshakeMessage;
import com.jtorrent.torrent.TorrentSession;

public class PeerManager {
	/**
	 * A list of all the peers returned from the tracker.
	 */
	private List<Peer> _peerList;
	private Map<String, Peer> _addressToPeerMap;
	private Map<String, Peer> _idToPeerMap;
	
	private Object _connectedLockObject;
	private Map<String, Peer> _connectedPeersMap;
	
	private final ConnectionService _connectionService;
	private final TorrentSession _torrentSession;
	
	public PeerManager(ConnectionService connService, TorrentSession session) {
		_peerList = new ArrayList<>();
		_addressToPeerMap = new HashMap<String, Peer>();
		_idToPeerMap = new HashMap<String, Peer>();
		
		_connectedLockObject = new Object();
		_connectedPeersMap = new HashMap<String, Peer>();
		_connectionService = connService;
		_torrentSession = session;
	}

	public synchronized List<Peer> getPeers() {
		return _peerList;
	}
	
	public synchronized void addAll(List<Peer> peerList) {
		for(Peer peer : peerList) {
			add(peer);
		}
	}
	
	public synchronized void add(Peer peer) {
		if (get(peer) != null) {
			return;
		}
		_peerList.add(peer);
		_addressToPeerMap.put(peer.getHostAddress(), peer);
		if(peer.getHexPeerID() != null && !peer.getHostAddress().isEmpty()) {
			_idToPeerMap.put(peer.getHexPeerID(), peer);
		}
	}
	
	public synchronized Peer get(Peer find) {
		Peer peer = findPeerByAddress(find);
		if (peer == null) {
			return findPeerByID(find);
		}
		
		return null;
	}
	
	public synchronized Peer findPeerByAddress(Peer find) {
		Peer peer = _addressToPeerMap.get(find.getHostAddress());
		if(peer != null) {
			// If we find a match then we update the peer id of the one
			// we have in our list and maps.
			if(find.getPeerID() != null && !find.getPeerID().isEmpty()) {
				peer.setPeerID(find.getPeerID());
				_idToPeerMap.put(find.getHexPeerID(), peer);
			}
			return peer;
		}
		
		return null;
	}
	
	public synchronized Peer findPeerByID(Peer find) {
		Peer peer = _idToPeerMap.get(find.getHexPeerID());
		if(peer != null) {
			// A peer can be identified by different host addresses. Make sure
			// that both are added in the map.
			_addressToPeerMap.put(peer.getHostAddress(), peer);
			_addressToPeerMap.put(find.getHostAddress(), peer);
			return peer;
		}
		return null;
	}
	
	public void registerConnection(Peer peer) {
		// TODO - see if peer exists
		synchronized (_connectedLockObject) {
			_connectedPeersMap.put(peer.getHexPeerID(), peer);
		}
	}
	
	public void registerConnectionAll(List<Peer> peers) {
		addAll(peers);
		ArrayList<Future<HandshakeResponse>> results = new ArrayList<Future<HandshakeResponse>>();
		for(Peer peer : _peerList) {
			results.add(_connectionService.connect(_torrentSession, peer));
		}
		
		for(Future<HandshakeResponse> handshakeResponse : results) {
			try {
				if(handshakeResponse == null) continue;
				
				SocketChannel channel = handshakeResponse.get().getSocketChannel();
				HandshakeMessage handshake = handshakeResponse.get().getHandshakeMessage();
				Peer peer = new Peer(channel.socket(), handshake.getPeerID());
				System.out.println("Connected to: " + peer.toString());
				registerConnection(peer);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ExecutionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
		// TODO - register the peer
	}
}
