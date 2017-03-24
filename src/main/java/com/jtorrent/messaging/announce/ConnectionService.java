package com.jtorrent.messaging.announce;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
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
import com.jtorrent.torrent.TorrentClient;
import com.jtorrent.torrent.TorrentSession;

// FIXME - add comment
public class ConnectionService {

	private static final Logger _logger = LoggerFactory.getLogger(ConnectionService.class);

	// The BitTorrent specification states that the range of the ports should be
	// 6881-6889 TCP. However, the client can use basically any ephemeral port:
	// http://www.ncftp.com/ncftpd/doc/misc/ephemeral_ports.html
	private static final int START_PORT = 49152;
	private static final int END_PORT = 65535;

	private static final int OUTBOUND_CONNECTIONS = 25;
	private static final long OUTBOUND_CONNECTIONS_KEEP_ALIVE_TIME = 20;

	private ServerSocketChannel _incommingChannel;

	private String _jtorrentPeerID;
	private InetSocketAddress _socketAddress;
	private ExecutorService _inboundConnectionsService;
	private boolean _listenForConnections;

	private final ExecutorService _outboundConnectionService;

	public ConnectionService() {
		_listenForConnections = false;
		setSocketAddress(bindToSocket());
		_outboundConnectionService = new ThreadPoolExecutor(OUTBOUND_CONNECTIONS, OUTBOUND_CONNECTIONS,
				OUTBOUND_CONNECTIONS_KEEP_ALIVE_TIME, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
	}

	private InetSocketAddress bindToSocket() {
		for (int port = START_PORT; port <= END_PORT; port++) {
			try {
				InetSocketAddress socketAddress = new InetSocketAddress(InetAddress.getLocalHost(), port);
				_incommingChannel = ServerSocketChannel.open();
				_incommingChannel.socket().bind(socketAddress);
				_incommingChannel.configureBlocking(false);
				return socketAddress;
			} catch (IOException e) {
				// Try the next port.
			}
		}

		return null;
	}

	public String getClientPeerID() {
		return _jtorrentPeerID;
	}

	public void setClientPeerID(String peerID) {
		_jtorrentPeerID = peerID;
	}

	public synchronized InetSocketAddress getSocketAddress() {
		return _socketAddress;
	}

	private synchronized void setSocketAddress(InetSocketAddress address) {
		_socketAddress = address;
	}

	public void stop() {
		_listenForConnections = false;
		if(!_outboundConnectionService.isShutdown() && !_outboundConnectionService.isTerminated()) {
			_outboundConnectionService.shutdown();
		}
		_inboundConnectionsService = null;
	}

	public void start() {
		if (_inboundConnectionsService != null) {
			return;
		}

		_inboundConnectionsService = Executors.newSingleThreadExecutor();
		_inboundConnectionsService.execute(new ListenTask());
		_listenForConnections = true;
	}
	
	public void cancel() throws IOException {
		if(_incommingChannel != null) {
			_incommingChannel.close();
			_incommingChannel = null;
		}
	}
	
	public Future<HandshakeResponse> connect(TorrentSession session, Peer peer) {
		return _outboundConnectionService.submit(new ConnectionTask(peer, session));
	}

	// TODO - P0 implement after implementing the downloading features
	// TODO - doc
	private class ListenTask implements Runnable {

		@Override
		public void run() {
			while (_listenForConnections) {

			}
		}

		private void accept() {
			// TODO - implemet
		}

		private void stop() {
			// TODO - release the channel and the executor
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
				ByteBuffer handshakeRequest = HandshakeMessage.make(_session.getMetaInfo().getInfoHash(), _jtorrentPeerID);
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
