package com.jtorrent.messaging.conn;

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
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.jtorrent.peer.Peer;
import com.jtorrent.torrent.TorrentSession;

public class ConnectionService {
	
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
	
	public ConnectionService(){
		_listenForConnections = false;
		setSocketAddress(bindToSocket());
		_outboundConnectionService = new ThreadPoolExecutor(
				OUTBOUND_CONNECTIONS, 
				OUTBOUND_CONNECTIONS, 
				OUTBOUND_CONNECTIONS_KEEP_ALIVE_TIME,
				TimeUnit.SECONDS, 
				new LinkedBlockingQueue<Runnable>());
	}

	private InetSocketAddress bindToSocket() {
		for(int port = START_PORT; port <= END_PORT; port++) {
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
	
	public void startListenForConnections() {
		if(_inboundConnectionsService != null) {
			return;
		}
		
		_inboundConnectionsService = Executors.newSingleThreadExecutor();
		_inboundConnectionsService.execute(new InboundConnectionsTask());
		_listenForConnections = true;
	}
	
	public void stopListenForConnections() {
		_listenForConnections = true;
	}
	
	public Future<HandshakeResponse> connect(TorrentSession session, Peer peer) {
		return _outboundConnectionService.submit(new OutboundConnectionTask(peer, session));
	}
	
	private class InboundConnectionsTask implements Runnable {
		
		@Override
		public void run() {
			while(_listenForConnections) {
				
			}
		}
		
		private void accept() {
			// TODO - implemet
		}
		
		private void stop() {
			// TODO - release the channel and the executor
		}
	}
	
	private class OutboundConnectionTask implements Callable<HandshakeResponse> {
		/**
		 * The peer that the torrent session wants to connect to and exhange
		 * pieces with.
		 */
		private Peer _peer;
		private TorrentSession _session;
		
		public OutboundConnectionTask(Peer peer, TorrentSession session) {
			_peer = peer;
			_session = session;
		}
		
		@Override
		public HandshakeResponse call() {
			try {
				SocketChannel channel = SocketChannel.open(_peer.getAddress());
				while(!channel.isConnected()) {
					TimeUnit.MILLISECONDS.sleep(10);
				}
				// The channel needs to wait for the handshake response from the peer.
				channel.configureBlocking(true);
				channel.write(HandshakeMessage.make(_session.getMetaInfo().getInfoHash(),
						_jtorrentPeerID));
				HandshakeMessage handshake = HandshakeMessage.validate(_session, channel, _peer.getPeerID());
				return new HandshakeResponse(handshake, channel);
			} catch (IOException | InterruptedException e) {
				// TODO log
			} catch (HandshakeException e) {
				// TODO log
				System.err.println(e);
			}
			return null;
		}		
	}
	
	public static class HandshakeResponse {
		private final HandshakeMessage _handshakeMessage;
		private final SocketChannel _socketChannel;
		
		public HandshakeResponse(HandshakeMessage hm, SocketChannel sc) {
			_handshakeMessage = hm;
			_socketChannel = sc;
		}
		
		public HandshakeMessage getHandshakeMessage() {
			return _handshakeMessage;
		}
		
		public SocketChannel getSocketChannel() {
			return _socketChannel;
		}
	}
}
