package com.jtorrent.peer;

import java.net.SocketException;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jtorrent.messaging.announce.ConnectionService;
import com.jtorrent.messaging.announce.HandshakeMessage;
import com.jtorrent.messaging.announce.ConnectionService.HandshakeResponse;
import com.jtorrent.torrent.TorrentSession;

/**
 * <p>Manages the list of peers that the tracker sends the client. It also keeps a record
 * of all the peers that the torrent session has made a connection to. </p>
 * <p><b>NOTE:</b>The methods in this class are thread safe. </p>
 * @author Alex
 *
 */
public class PeerManager {
	
	private static final Logger _logger = LoggerFactory.getLogger(PeerManager.class);
	
	/**
	 * <p>A  &lt; IP:port, Peer &gt; map.</p>
	 * <b>NOTE:</b> each peers has a mandatory ip:port host address and so this map
	 * is guaranteed to have all unique peers that we want to connect to.
	 */
	private Map<String, Peer> _addressToPeerMap;
	private Map<String, Peer> _idToPeerMap;
	
	private Object _connectedLockObject;
	private Map<String, Peer> _connectedPeersMap;
	
	private final ConnectionService _connectionService;
	private final TorrentSession _torrentSession;	
	private final ExecutorService _registerService;
	
	public PeerManager(ConnectionService connService, TorrentSession session) {
		_addressToPeerMap = new HashMap<String, Peer>();
		_idToPeerMap = new HashMap<String, Peer>();
		
		_connectedLockObject = new Object();
		_connectedPeersMap = new HashMap<String, Peer>();
		_connectionService = connService;
		_torrentSession = session;
		_registerService = Executors.newCachedThreadPool();
	}
	
	public void cleanup() {
		_registerService.shutdown();
	}
	
	public synchronized void addAll(List<Peer> peerList) {
		for(Peer peer : peerList) {
			add(peer);
		}
	}
	
	/**
	 * Adds a peer to the list of peers that the client can later connect to.
	 * In order to connect to a peer see {@link ConnectionService}.
	 * @param peer
	 */
	public synchronized void add(Peer peer) {
		if (_addressToPeerMap.containsKey(peer.getHostAddress())) {
			return;
		}
		
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
	
	private synchronized Peer findPeerByID(Peer find) {
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
	
	/**
	 * The Peer manager registers a new connection. The peer is bound to a socket and
	 * it gets a peer id if it has none (since we ask for a compact list of peers).
	 * @param tryPeer The peer we are try to connect to
	 * @param channel The channel to which to peer is to be bound
	 */
	public void registerConnection(Peer tryPeer, SocketChannel channel) {
		// See if we hate the peer in the list of peers. If not - add it.
		// When the client fails connect to a peer, the peer is removed
		Peer peer = get(tryPeer);
		if (peer == null) {
			peer = tryPeer;
			add(peer);
		}
		_logger.info("Trying to register {}...", peer);
		synchronized (_connectedLockObject) {
			try {
			if(peer.isConnected()) {
				// The peer is already connected and is exchanging on a channel.
				// Close the channel and move on.
				IOUtils.closeQuietly(channel);
				return;
			}
			
			peer.bind(channel);
			_connectedPeersMap.put(peer.getHexPeerID(), peer);
			if(peer.getPeerID() == null) {
				peer.setPeerID(tryPeer.getPeerID());
			}
			
			_idToPeerMap.put(peer.getPeerID(), peer);
			_logger.debug("registered {}", peer);
			} catch (SocketException e) {
				_logger.warn("Could not register new peer {}. Reason: {}", peer, e.getMessage());
				_connectedPeersMap.remove(peer.getHexPeerID());
			}
		}
	}
	
	/**
	 * Attempts to register the list of peers with  the peer manager.
	 * @param peers The list of peers that will be registered.
	 */
	public void registerConnectionAll(List<Peer> peers) {
		addAll(peers);
		ArrayList<Future<HandshakeResponse>> results = new ArrayList<Future<HandshakeResponse>>();
		for(Peer peer : _addressToPeerMap.values()) {
			results.add(_connectionService.connect(_torrentSession, peer));
		}
		
		for(Future<HandshakeResponse> handshakeResponseFuture : results) {
			_registerService.execute(new RegisterTask(handshakeResponseFuture));
		}
	}
	
	private void handleHandshakeUnsuccessful(Exception e, Peer p) {
		_logger.debug("An error {} occured while handshaking with {}", p, e.getMessage());
		_addressToPeerMap.remove(p.getHostAddress());
		if(p.getPeerID() != null) {
			_idToPeerMap.remove(p.getHexPeerID());
		}
	}
	
	/**
	 * This task receives a result of sending a handshake message to a peer. It then
	 * attempts to register the peer with the peer manager by waiting for a response
	 * from the thread that is responsible for the communication between the peer and
	 * the client (the torrent session is the client in this case). 
	 * @author Alex
	 *
	 */
	private class RegisterTask implements Runnable {
		private Future<HandshakeResponse> _future;
		
		public RegisterTask(Future<HandshakeResponse> future) {
			_future = future;
		}
		
		
		@Override
		public void run() {
			HandshakeResponse handshakeResponse;
			try {
				handshakeResponse = _future.get();
				if(handshakeResponse.getError() != null) {
					handleHandshakeUnsuccessful(handshakeResponse.getError(), handshakeResponse.getTryPeer());
					return;
				}
				// Extract the communication socket channel and the handshake response.
				SocketChannel channel = handshakeResponse.getSocketChannel();
				HandshakeMessage handshake = handshakeResponse.getHandshakeMessage();
				Peer peer = new Peer(channel.socket(), handshake.getPeerID());
				
				// Try to register the connection with the peer manager.
				registerConnection(peer, channel);
			}  catch (InterruptedException e) {
				_logger.debug("Registriation interrupted: {}", e.getMessage());
			} catch (ExecutionException e) {
				_logger.debug("Unable to retrieve result from future {}", e.getMessage());
			}
		}		
	}
}
