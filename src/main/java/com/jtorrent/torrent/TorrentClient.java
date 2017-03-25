package com.jtorrent.torrent;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jtorrent.messaging.announce.ConnectionService;
import com.jtorrent.peer.Peer;

/**
 * This is experimental
 */
public class TorrentClient {
	public static final String BITTORRENT_ID_PREFIX = "-TO0042-";

	private ConnectionService _connectionService;
	private ExecutorService _sessionExecutor;

	// see https://logback.qos.ch/manual/introduction.html
	private static final Logger _logger = LoggerFactory.getLogger(TorrentClient.class);

	public TorrentClient() {
		_connectionService = new ConnectionService();
		_sessionExecutor = Executors.newCachedThreadPool();
	}

	public void registerNewSession(String fileName, String destination)
			throws InterruptedException, ExecutionException {
		/*Future<Boolean> result = _sessionExecutor
				.submit(new TorrentSessionTask(_connectionService, fileName, destination));
		return result.get();*/
		
		_sessionExecutor.execute(new TorrentSessionTask(_connectionService, fileName, destination));
	}

	public void shutdown() {
		_sessionExecutor.shutdown();
	}

	private static class TorrentSessionTask implements Runnable {
		private Peer _clientPeer;
		private String _fileName;
		private String _destination;
		private ConnectionService _connectionService;

		public TorrentSessionTask(ConnectionService connectionService, String fileName, String destination) {
			_fileName = fileName;
			_destination = destination;
			_connectionService = connectionService;

			String id = BITTORRENT_ID_PREFIX + UUID.randomUUID().toString().split("-")[4];
			try {
				_connectionService.setClientPeerID(new String(id.getBytes(TorrentSession.BYTE_ENCODING)));
				_clientPeer = new Peer(_connectionService.getSocketAddress().getAddress().getHostAddress(),
						_connectionService.getSocketAddress().getPort(), _connectionService.getClientPeerID());
			} catch (UnsupportedEncodingException e) {
				_logger.warn("Exception occured while starting torrent session for {}: {}",
						fileName, e.getMessage());
			}
		}

		@SuppressWarnings("unused")
		@Override
		public void run() {
			TorrentSession ts = null;
			try {
				ts = new TorrentSession(_fileName, _destination, _clientPeer, _connectionService);
				_connectionService.register(ts);
				ts.start();
			} catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException |
					IOException| URISyntaxException e) {
				if(ts != null) {
					ts.stop();
				}
			} 
		}
	}

	public ConnectionService getConnectionService() {
		return _connectionService;
	}
	
	public void start() {
		_connectionService.start();
	}
	
	public void stop() {
		_connectionService.stop();
		try {
			_connectionService.cancel();
		} catch (IOException e) {
			// Ignore
		}
	}

	public static void main(String[] args) {

		// Future<Boolean> result2 = es.submit(new SessionTask(clientPeer,
		// "D:/Movie/t1.torrent", "D:/Movie/dir"));
		TorrentClient client = new TorrentClient();
		client.start();
		try {
			client.registerNewSession("D:/Movie/orig.torrent", "D:/Movie/dir");
			//client.registerNewSession("D:/Movie/minecraft.torrent", "D:/Movie/dir");
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// "D:/Movie/assas.torrent", "D:/Movie/dir"
	}

}
