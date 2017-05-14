package com.jtorrent.peer;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jtorrent.messaging.announce.ConnectionService;
import com.jtorrent.messaging.announce.ConnectionService.HandshakeResponse;
import com.jtorrent.messaging.announce.HandshakeMessage;
import com.jtorrent.messaging.rate.DownloadRateComparator;
import com.jtorrent.messaging.rate.UploadRateComparator;
import com.jtorrent.torrent.TorrentSession;

/**
 * <p>
 * Manages the list of peers that the tracker sends the client. It also keeps a
 * record of all the peers that the torrent session has made a connection to.
 * </p>
 * <p>
 * <b>NOTE:</b>The methods in this class are thread safe.
 * </p>
 * 
 * @author Alex
 *
 */
public class PeerManager implements PeerStateListener {

	public static final int MAX_TOTAL_NUMBER_OF_PEERS = 80;
	public static final int MAX_NUMBER_OF_CONNECTED_PEERS = 40;
	public static final int MIN_NUMBER_OF_CONNECTED_PEERS = 20;
	
	private static final int OPTIMISTIC_UNCHOKE_ROTATIONS = 3;
	private static final int RATE_COMPUTATION_ROTATIONS = 2;
	private static final int BEST_RECIPROCATION_PEERS = 4;
	private static final int UNCHOKING_SLEEP_DURATION_SECS = 10;
	
	
	private static final Logger _logger = LoggerFactory.getLogger(PeerManager.class);

	/**
	 * <p>
	 * A &lt; IP:port, Peer &gt; map.
	 * </p>
	 * <b>NOTE:</b> each peers has a mandatory ip:port host address and so this
	 * map is guaranteed to have all unique peers that we want to connect to.
	 */
	private Map<String, Peer> _addressToPeerMap;
	private Map<String, Peer> _idToPeerMap;

	private Object _connectedLockObject;
	private Map<String, Peer> _connectedPeersMap;

	private final ConnectionService _connectionService;
	private final TorrentSession _torrentSession;
	private final ExecutorService _registerService;
	private final Thread _chokerThread;
	
	private volatile boolean _stop;
	
	private final List<Future<HandshakeResponse>> _connectionFutures;

	public PeerManager(ConnectionService connService, TorrentSession session) {
		_addressToPeerMap = new HashMap<String, Peer>();
		_idToPeerMap = new HashMap<String, Peer>();

		_connectedLockObject = new Object();
		_connectedPeersMap = new HashMap<String, Peer>();
		_connectionService = connService;
		_torrentSession = session;
		_registerService = Executors.newCachedThreadPool();
		
		_chokerThread = new Thread(new ChokerTask());
		
		_connectionFutures = new ArrayList<Future<HandshakeResponse>>();
	}

	public void cleanup() {
		_registerService.shutdownNow();
		_logger.debug("PeerManager:ReisgerService shut down");
	}

	public synchronized void set(List<Peer> peerList) {
		_addressToPeerMap = new HashMap<String, Peer>();
		_idToPeerMap = new HashMap<String, Peer>();
		
		for (Peer peer : peerList) {
			add(peer);
		}
	}

	/**
	 * Adds a peer to the list of peers that the client can later connect to. In
	 * order to connect to a peer see {@link ConnectionService}.
	 * 
	 * @param peer
	 */
	public synchronized void add(Peer peer) {
		if (_addressToPeerMap.containsKey(peer.getHostAddress())) {
			return;
		}

		_addressToPeerMap.put(peer.getHostAddress(), peer);
		if (peer.getHexPeerID() != null && !peer.getHostAddress().isEmpty()) {
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
		if (peer != null) {
			// If we find a match then we update the peer id of the one
			// we have in our list and maps.
			if (find.getPeerID() != null && !find.getPeerID().isEmpty()) {
				peer.setPeerID(find.getPeerID());
				_idToPeerMap.put(find.getHexPeerID(), peer);
			}
			return peer;
		}

		return null;
	}

	private synchronized Peer findPeerByID(Peer find) {
		Peer peer = _idToPeerMap.get(find.getHexPeerID());
		if (peer != null) {
			// A peer can be identified by different host addresses. Make sure
			// that both are added in the map.
			_addressToPeerMap.put(peer.getHostAddress(), peer);
			_addressToPeerMap.put(find.getHostAddress(), peer);
			return peer;
		}
		return null;
	}

	/**
	 * The Peer manager registers a new connection. The peer is bound to a
	 * socket and it gets a peer id if it has none (since we ask for a compact
	 * list of peers).
	 * 
	 * @param tryPeer
	 *            The peer we are try to connect to
	 * @param channel
	 *            The channel to which to peer is to be bound
	 */
	public void registerConnection(Peer tryPeer, SocketChannel channel) {
		// Limit the number of connected peers.
		if(_connectedPeersMap.size() == MAX_NUMBER_OF_CONNECTED_PEERS) {
			return;
		}
		
		if(tryPeer.getPeerID() == null) {
			return;
		}
		
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
				if (peer.isConnected()) {
					// The peer is already connected and is exchanging on a
					// channel.
					// Close the channel and move on.
					IOUtils.closeQuietly(channel);
					return;
				}

				peer.bind(_torrentSession, channel);
				peer.addStateListener(this);
				_connectedPeersMap.put(peer.getHexPeerID(), peer);
				if (peer.getPeerID() == null) {
					peer.setPeerID(tryPeer.getPeerID());
				}

				_idToPeerMap.put(peer.getPeerID(), peer);
				// Register the peer with the piece repository.
				_torrentSession.getPieceRepository().register(peer);
				_logger.debug("registered {}", peer);
			} catch (IOException e) {
				_logger.warn("Could not register new peer {}. Reason: {}", peer, e.getMessage());
				_connectedPeersMap.remove(peer.getHexPeerID());
			}
		}
	}

	/**
	 * Attempts to register the list of peers with the peer manager.
	 * 
	 * @param peers
	 *            The list of peers that will be registered.
	 */
	public void registerConnectionAll(List<Peer> peers) {
		set(peers);
		for (Peer peer : _addressToPeerMap.values()) {
			Future<HandshakeResponse> future = _connectionService.connect(_torrentSession, peer);
			if(future == null) {
				continue;
			}
			
			_connectionFutures.add(future);
		}
		
		for (Future<HandshakeResponse> handshakeResponseFuture : _connectionFutures) {
			_registerService.execute(new RegisterTask(handshakeResponseFuture));
		}
	}

	private void handleHandshakeUnsuccessful(Exception e, Peer p) {
		_logger.debug("An error {} occured while handshaking with {}", p, e.getMessage());
		_addressToPeerMap.remove(p.getHostAddress());
		if (p.getPeerID() != null) {
			_idToPeerMap.remove(p.getHexPeerID());
		}
	}

	public Set<Peer> getPeers() {
		return new HashSet<Peer>(_addressToPeerMap.values());
	}
	
	public Set<Peer> getConnectedPeers() {
		synchronized (_connectedLockObject) {
			return new HashSet<Peer>(_connectedPeersMap.values());
		}
	}

	/**
	 * This task receives a result of sending a handshake message to a peer. It
	 * then attempts to register the peer with the peer manager by waiting for a
	 * response from the thread that is responsible for the communication
	 * between the peer and the client (the torrent session is the client in
	 * this case).
	 * 
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
				if (handshakeResponse.getError() != null) {
					handleHandshakeUnsuccessful(handshakeResponse.getError(), handshakeResponse.getTryPeer());
					return;
				}
				// Extract the communication socket channel and the handshake
				// response.
				SocketChannel channel = handshakeResponse.getSocketChannel();
				HandshakeMessage handshake = handshakeResponse.getHandshakeMessage();
				Peer peer = new Peer(channel.socket(), handshake.getPeerID());
				// If the torrent session has been stopped, then no new peers should be added.
				if(_stop) {
					return;
				}
				// Try to register the connection with the peer manager.
				registerConnection(peer, channel);
			} catch (InterruptedException e) {
				_logger.debug("Registriation interrupted: {}", e.getMessage());
			} catch (ExecutionException e) {
				_logger.debug("Unable to retrieve result from future {}", e.getMessage());
			} catch (CancellationException e) {
				// Do nothing.
			}
		}
	}
	
	public void start() {
		_stop = false;
		_chokerThread.start();
	}
	
	public void stop() throws InterruptedException {
		_stop = true;
		_chokerThread.join();
		_addressToPeerMap = new HashMap<String, Peer>();
		_idToPeerMap = new HashMap<String, Peer>();

		// Cancel all currently running connection attempts.
		for(Future<HandshakeResponse> future : _connectionFutures) {
			future.cancel(true);
		}
		_logger.debug("Peer manager stopped");
	}
	
	public synchronized void disconnectAllConcurrently() throws InterruptedException {
		ExecutorService executor = Executors.newSingleThreadExecutor();
		
		List<Callable<Boolean>> callables = new ArrayList<Callable<Boolean>>();
		
		for(Peer peer: getConnectedPeers()) {
			callables.add(()-> {
				peer.unbind(true);
				return true;
			});
		}
		
		executor.invokeAll(callables);
		
		try {
		    executor.shutdownNow();
		}
		finally {
		    if (!executor.isTerminated()) {
		        _logger.debug("Cancelling non-finished tasks.");
		    }
		    executor.shutdownNow();
		    _logger.debug("Disconnecting finished successfully.");
		}
	}
	
	private class ChokerTask implements Runnable {

		@Override
		public void run() {
			_logger.debug("Running choker task...");
			try {
				manageChokingAndOptimisticUnchoking();
			} catch (InterruptedException e) {
				_logger.warn("Excewption while choking: {}", e.getMessage());
			}
		}
		
	
		public synchronized void manageChokingAndOptimisticUnchoking() 
				throws InterruptedException {
			// Every 30 seconds a peer is unchoked optimistically.
			// A peer can be unchoked every 10 seconds, so this means every 3rd rotation
			// a peer can be unchoked optimistically.
			int reamimingUnchokingRotations = 0;
			int computeRateRotations = 0;
			
			while(!_stop) {
				
				if(reamimingUnchokingRotations == 0) {
					reamimingUnchokingRotations = OPTIMISTIC_UNCHOKE_ROTATIONS;
				} else {
					reamimingUnchokingRotations--;
				}
				
				if(computeRateRotations == 0) {
					computeRateRotations = RATE_COMPUTATION_ROTATIONS;
				} else {
					computeRateRotations--;
				}
				
				managePeers(reamimingUnchokingRotations == 0);
				
				if (computeRateRotations == 0) {
					for(Peer peer : _connectedPeersMap.values()) {
						peer.getDownloadRate().reset();
						peer.getUploadRate().reset();
					}
				}
				
				TimeUnit.SECONDS.sleep(UNCHOKING_SLEEP_DURATION_SECS);
			}
		}
		
		public synchronized void managePeers(boolean inOptimisticRotation) {
			if(_stop) {
				return;
			}
			Comparator<Peer> rateComparator;
			try {
				rateComparator = provideRateComparator();
			} catch(IllegalArgumentException e) {
				_logger.trace("Could not find rate comparator this time");
				return;
			}
			TreeSet<Peer> connectedPeers = new TreeSet<>(rateComparator);
			connectedPeers.addAll(_connectedPeersMap.values());
			if(connectedPeers.size() == 0) {
				_logger.debug("No connected peers at this time.");
				return;
			} else {
				_logger.debug("Managing choke/unchoke on {} connected peers",
						connectedPeers.size());
			}
			
			int downloadingPeers = 0;
			// Get the best 4 uploaders first.
			Set<Peer> amChokingSet = new HashSet<>();
			for(Peer peer : connectedPeers.descendingSet()) {
				if(downloadingPeers < BEST_RECIPROCATION_PEERS) {
					if(peer.getAmChoking()) {
						if(peer.getPeerInterested()) {
							downloadingPeers++;
						}
						
						peer.setAmChoking(false);
					}
				} else {
					amChokingSet.add(peer);
				}
			}
			
			// Choke all the other peers.
			// During the optimistic rotation one peer has to remain, though, and it is chosen
			// are random.
			if(amChokingSet.size() > 0) {
				Random random = new Random(System.currentTimeMillis());
				Peer[] amChokingArray = amChokingSet.toArray(new Peer[0]);
				int randomPeerPos = random.nextInt(amChokingSet.size());
				Peer optimisticPeer = amChokingArray[randomPeerPos];
				
				for(Peer peer : amChokingArray) {
					if(inOptimisticRotation && peer == optimisticPeer) {
						peer.setAmChoking(false);
					}
					
					peer.setAmChoking(true);
				}
			}
		}
		
		public Comparator<Peer> provideRateComparator() throws IllegalStateException{
			if(_stop) {
				return null;
			}
			if(_torrentSession.getStatus().equals(TorrentSession.Status.DOWNLOADING)) {
				return new DownloadRateComparator();
			} else if(_torrentSession.getStatus().equals(TorrentSession.Status.SEEDING)) {
				return new UploadRateComparator();
			} else {
				throw new IllegalStateException("The torrent session is not downloading or seeding.");
			}
		}
	}

	@Override
	public void onPeerDisconnected(Peer peer) {
		synchronized (_connectedLockObject) {
			_connectedPeersMap.remove(peer.getHexPeerID());
			_logger.debug("Peer {} disconnected, leaving {} connected peers", peer.getHostAddress(), _connectedPeersMap.values().size());
		}
	}
	
	public static class Rates {
		private double _downloadRate;
		private double _uploadRate;
		
		public Rates(double downloadRate, double uploadRate) {
			_downloadRate = downloadRate;
			_uploadRate = uploadRate;
		}
		
		public double getDownloadRate() {
			return _downloadRate;
		}
		
		public double getDownloadRatePerSec() {
			return _downloadRate / 1024.0;
		}
		
		public double getUploadRate() {
			return _uploadRate;
		}
		
		public double getUploadRatePerSec() {
			return _uploadRate / 1024.0;
		}
	}
	
	public Rates getRates() {
		double totalDonwloadRate = 0;
		double totalUploadRate = 0;
		for (Peer peer : _connectedPeersMap.values()) {
			totalDonwloadRate += peer.getDownloadRate().rate();
			totalUploadRate += peer.getUploadRate().rate();
		}
		
		return new Rates(totalDonwloadRate, totalUploadRate);
	}
}
