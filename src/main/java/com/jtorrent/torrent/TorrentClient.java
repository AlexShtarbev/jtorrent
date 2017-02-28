package com.jtorrent.torrent;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.nio.channels.ServerSocketChannel;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.jtorrent.peer.Peer;

/**
 * This is experimental 
 */
public class TorrentClient {
	private static final String BITTORRENT_ID_PREFIX = "-TO0042-";
	public static final int PORT_RANGE_START = 49152;
	public static final int PORT_RANGE_END = 65534;
	
	public static void main(String[] args) {
		ServerSocketChannel channel = null;
		InetSocketAddress tryAddress = null;
		// Bind to the first available port in the range
		// [PORT_RANGE_START; PORT_RANGE_END].
		for (int port = PORT_RANGE_START;
				port <= PORT_RANGE_END;
				port++) {
			try {
				tryAddress = new InetSocketAddress(InetAddress.getLocalHost(), port);
				channel = ServerSocketChannel.open();
				channel.socket().bind(tryAddress);
				channel.configureBlocking(false);
				break;
			} catch (IOException ioe) {
				// Ignore, try next port
				ioe.printStackTrace();
			}
		}

		if (channel == null || !channel.socket().isBound()) {
			System.err.println("No available port for the BitTorrent client!");
			System.exit(0);
		}
		
		String id = BITTORRENT_ID_PREFIX + UUID.randomUUID()
		.toString().split("-")[4];
		
		Peer clientPeer = null;
		try {
			clientPeer = new Peer(tryAddress.getAddress().getHostAddress(),
					tryAddress.getPort(), new String(id.getBytes(TorrentSession.BYTE_ENCODING)));
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(0);
		}
		
		ExecutorService es = Executors.newCachedThreadPool();
		Future<Boolean> result = es.submit(new SessionTask(clientPeer, "D:/Movie/assas.torrent", "D:/Movie/dir"));
		//Future<Boolean> result2 = es.submit(new SessionTask(clientPeer, "D:/Movie/t1.torrent", "D:/Movie/dir"));
		es.shutdown();
	}
	
	private static class SessionTask implements Callable<Boolean> {
		private final Peer _clientPeer;
		private final String _fileName;
		private final String _destination;
		
		public SessionTask(Peer peer, String fileName, String destination) {
			_clientPeer = peer;
			_fileName = fileName;
			_destination = destination;
		}

		@Override
		public Boolean call() throws Exception {
			try {
				TorrentSession ts = new TorrentSession(_fileName, _destination, _clientPeer);
				ts.startSession();
				try {
					Thread.sleep(10000);
					ts.stopSession();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return true;
			} catch (NoSuchAlgorithmException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InvalidAlgorithmParameterException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (URISyntaxException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			return false;
	
		}
		
	}

}
