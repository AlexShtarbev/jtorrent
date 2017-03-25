package com.jtorrent.messaging.announce;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jtorrent.peer.Peer;
import com.jtorrent.torrent.TorrentSession;
import com.jtorrent.utils.Utils;

/**
 * ConnectionService is an abstraction that allows for Torrent Sessions to connect to 
 * seeding peers via the {@link #connect()} and listen for incoming
 * peer connections by invoking the {@link #register()}. A Torrent Session
 * can opt out of accepting incoming connections by invoking the {@link #unregister()}
 * method.
 * @author Alex
 *
 */
public class ConnectionService {

	private static final Logger _logger = LoggerFactory.getLogger(ConnectionService.class);

	// The BitTorrent specification states that the range of the ports should be
	// 6881-6889 TCP. However, the client can use basically any ephemeral port:
	// http://www.ncftp.com/ncftpd/doc/misc/ephemeral_ports.html
	public static final int START_PORT = 49152;
	public static final int END_PORT = 65535;

	private static final int NUMBER_OF_SIMULTANIOUS_CONNECTIONS = 25;
	private static final long KEEP_ALIVE_TIME = 20;
	
	private static final int LISTEN_SLEEP_DURATION = 50;

	/**
	 * Used for listening for incoming connections.
	 */

	private String _clientPeerID;
	private Map<String, TorrentSession> _registeredTorrents;
	
	private ServerSocketChannel _listenChannel;
	private InetSocketAddress _socketAddress;
	private ExecutorService _listeningService;
	private boolean _listenForConnections;
	
	private ExecutorService _connectionService;

	public ConnectionService() {
		_listenForConnections = false;
		_connectionService = new ThreadPoolExecutor(
				NUMBER_OF_SIMULTANIOUS_CONNECTIONS,
				NUMBER_OF_SIMULTANIOUS_CONNECTIONS,
				KEEP_ALIVE_TIME,
				TimeUnit.SECONDS,
				new LinkedBlockingQueue<Runnable>());
		
		_registeredTorrents = new HashMap<>();
	}

	private InetSocketAddress bindToSocket() {
		for (int port = START_PORT; port <= END_PORT; port++) {
			try {
				InetSocketAddress socketAddress = new InetSocketAddress(InetAddress.getLocalHost(), port);
				_listenChannel = ServerSocketChannel.open();
				_listenChannel.socket().bind(socketAddress);
				_listenChannel.configureBlocking(false);
				_logger.debug("Opened on port {}", port);
				return socketAddress;
			} catch (IOException e) {
				// Try the next port.
			}
		}

		return null;
	}

	public String getClientPeerID() {
		return _clientPeerID;
	}

	public void setClientPeerID(String peerID) {
		_clientPeerID = peerID;
	}

	public synchronized InetSocketAddress getSocketAddress() {
		return _socketAddress;
	}

	private synchronized void setSocketAddress(InetSocketAddress address) {
		_socketAddress = address;
	}
	
	public synchronized void register(TorrentSession session) {
		if(session == null) {
			return;
		}
		_registeredTorrents.put(session.getMetaInfo().getHexInfoHash(), session);
	}
	
	public synchronized void unregister(TorrentSession session) {
		if(session == null) {
			return;
		}
		_registeredTorrents.remove(session.getMetaInfo().getHexInfoHash());
	}	

	public void start() throws IllegalStateException {
		if(_socketAddress != null) {
			return;
		}
		
		InetSocketAddress clientAddress = bindToSocket();
		if(clientAddress == null) {
			throw new IllegalStateException("Could not find a port to bind to");
		}
		setSocketAddress(clientAddress);
		
		if (_listeningService != null) {
			return;
		}
				
		_listeningService = Executors.newSingleThreadExecutor();
		_listeningService.execute(new ListenTask());
		_listenForConnections = true;
	}

	public void stop() {
		_listenForConnections = false;
		if(!_connectionService.isShutdown() && !_connectionService.isTerminated()) {
			_connectionService.shutdown();
		}
		
		if(!_listeningService.isShutdown() && !_listeningService.isTerminated()) {
			_listeningService.shutdown();
		}
		
		_connectionService = null;
		_listeningService = null;
		
		for(TorrentSession ts : _registeredTorrents.values()) {
			unregister(ts);
		}
	}
	
	public void cancel() throws IOException {
		if(_listenChannel != null) {
			_listenChannel.close();
			_listenChannel = null;
		}
	}
	
	public Future<HandshakeResponse> connect(TorrentSession session, Peer peer) {
		if(_connectionService != null && !_connectionService.isShutdown() 
				&& !_connectionService.isTerminated()) {
			return _connectionService.submit(new ConnectionTask(peer, session));
		}
		
		return null;
	}

	// TODO - doc
	private class ListenTask implements Runnable {

		@Override
		public void run() {
			while (_listenForConnections) {
				try {
					SocketChannel connectionChannel = _listenChannel.accept();
					if(connectionChannel != null) {
						_logger.debug("Handling new connection...");
						connect(connectionChannel);
					}
				} catch (IOException e) {
					_logger.warn("Torrent client channel is unavailable. Terminating...");
					stop();
				}
				
				try {
					TimeUnit.MILLISECONDS.sleep(LISTEN_SLEEP_DURATION);
				} catch (InterruptedException e) {
					stop();
				}
			}
		}

		private void connect(SocketChannel channel) {
			InetAddress address = channel.socket().getInetAddress();
			try {
				HandshakeMessage msg = HandshakeMessage.parse(channel);
				if(msg == null) {
					return;
				}
				
				TorrentSession session = _registeredTorrents.get(Utils.convertToHex(msg.getInfoHash()));
				
				boolean check = HandshakeMessage.check(session.getMetaInfo().getInfoHash(),
						msg, _clientPeerID, address);
				if(!check) {
					_logger.warn("Invalid handshake message from {}", address.toString());
					return;
				}
				
				ByteBuffer handshake = HandshakeMessage.make(session.getMetaInfo().getInfoHash(),
						_clientPeerID);
				channel.write(handshake);
				Peer peer = new Peer(channel.socket(), msg.getPeerID());
				channel.configureBlocking(false);
				session.getPeerManager().registerConnection(peer, channel);
			} catch (HandshakeException | UnsupportedEncodingException e) {
				_logger.warn("Could not parse handhsake message from {}:{} - {}", address.toString(), channel.socket().getPort(),
						e.getMessage());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	private class ConnectionTask implements Callable<HandshakeResponse> {
		/**
		 * The peer that the torrent session wants to connect to and exhange
		 * pieces with.
		 */
		private Peer _peer;
		private TorrentSession _session;

		public ConnectionTask(Peer peer, TorrentSession session) {
			_peer = peer;
			_session = session;
		}

		@Override
		public HandshakeResponse call() {
			SocketChannel channel = null;
			try {
				channel = SocketChannel.open(_peer.getAddress());
				_logger.debug("Trying to connect to {}", _peer);
				while (!channel.isConnected()) {
					TimeUnit.MILLISECONDS.sleep(10);
				}
				// The channel needs to wait for the handshake response from the
				// peer.
				channel.configureBlocking(true);
				_logger.debug("Sending handshake to {}", _peer);
				ByteBuffer handshakeRequest = HandshakeMessage.make(_session.getMetaInfo().getInfoHash(), _clientPeerID);
				int sent = channel.write(handshakeRequest);
				_logger.debug("sent {} bytes handshake to {}", sent, _peer.toString());
				HandshakeMessage handshake = HandshakeMessage.validate(_session, channel, _peer.getPeerID());
				_logger.debug("Received handshake from {}", _peer);
				channel.configureBlocking(false);
				return new HandshakeResponse(handshake, channel, _peer);

			} catch (IOException | InterruptedException | HandshakeException e) {
				_logger.debug("Could not connect to {}. Reason: ", _peer, e.getMessage());
				if (channel != null && channel.isConnected()) {
					IOUtils.closeQuietly(channel);
				}
				return new HandshakeResponse(null, null, _peer, e);
			}
		}
	}

	public static class HandshakeResponse {
		private final HandshakeMessage _handshakeMessage;
		private final SocketChannel _socketChannel;
		private Exception _e;
		/**
		 * The peer we are trying to connect to.
		 */
		private Peer _tryPeer;

		public HandshakeResponse(HandshakeMessage hm, SocketChannel sc, Peer peer) {
			this(hm, sc, peer, null);
		}

		public HandshakeResponse(HandshakeMessage hm, SocketChannel sc, Peer peer, Exception e) {
			_handshakeMessage = hm;
			_socketChannel = sc;
			_e = e;
			_tryPeer = peer;
		}

		public HandshakeMessage getHandshakeMessage() {
			return _handshakeMessage;
		}

		public SocketChannel getSocketChannel() {
			return _socketChannel;
		}

		public Exception getError() {
			return _e;
		}

		/**
		 * 
		 * @return The peer that the client tried to connect to.
		 */
		public Peer getTryPeer() {
			return _tryPeer;
		}
	}
}
