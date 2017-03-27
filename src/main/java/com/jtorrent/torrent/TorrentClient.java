package com.jtorrent.torrent;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jtorrent.messaging.announce.ConnectionService;
import com.jtorrent.peer.Peer;

/**
 * This is experimental
 */
public class TorrentClient implements TorrentSessionEventListener {
	public static final String BITTORRENT_ID_PREFIX = "-TO0042-";
	
	public static final int MAX_DOWNLOADING_TORRENTS = 5;
	public static final int MAX_TORRENTS = 30;
	
	private ConnectionService _connectionService;
	private ExecutorService _sessionExecutor;
	
	private List<TorrentSessionTask> _torrentQueue;
	private List<TorrentSession> _activeSessions;
	private int _downloading;

	// see https://logback.qos.ch/manual/introduction.html
	private static final Logger _logger = LoggerFactory.getLogger(TorrentClient.class);

	public TorrentClient() {
		_connectionService = new ConnectionService();
		_sessionExecutor = Executors.newCachedThreadPool();
		
		_torrentQueue = new LinkedList<TorrentSessionTask>();
		_activeSessions = new LinkedList<TorrentSession>();
	}

	public synchronized void startNewSession(String fileName, String destination)
			throws InterruptedException, ExecutionException, QueueingException {
		TorrentSessionTask task = new TorrentSessionTask(_connectionService, fileName, destination);
		if(_activeSessions.size() + _torrentQueue.size() == MAX_TORRENTS) {
			throw new QueueingException("Reach max number of torrents.");
		}
		
		if(_downloading == MAX_DOWNLOADING_TORRENTS) {
			_torrentQueue.add(task);
		} else {
			_downloading++;
			_sessionExecutor.execute(task);
		}
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
		_sessionExecutor.shutdownNow();
	}	

	public ConnectionService getConnectionService() {
		return _connectionService;
	}

	private synchronized void addNewActiveSession(TorrentSession session) {
		_activeSessions.add(session);
	}	

	@Override
	public synchronized void onDownloadComplete() {
		_downloading--;
		if(!_torrentQueue.isEmpty()) {
			TorrentSessionTask waiting = ((LinkedList<TorrentSessionTask>)_torrentQueue).poll();
			_sessionExecutor.execute(waiting);
		}
	}

	private class TorrentSessionTask implements Runnable {
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
				_clientPeer = new Peer(
						_connectionService.getSocketAddress().getAddress().getHostAddress(),
						_connectionService.getSocketAddress().getPort(),
						_connectionService.getClientPeerID());
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
				addNewActiveSession(ts);
				ts.register(TorrentClient.this);
				ts.start();
			} catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException |
					IOException| URISyntaxException e) {
				if(ts != null) {
					ts.stop();
				}
			} 
		}
	}

	@SuppressWarnings("serial")
	public static class QueueingException extends Exception {
		public QueueingException() {
			super();
		}
		
		public QueueingException(String args) {
			super(args);
		}
	}
		
	// FIXME - remove
	public static void main(String[] args) {

		// Future<Boolean> result2 = es.submit(new SessionTask(clientPeer,
		// "D:/Movie/t1.torrent", "D:/Movie/dir"));
		// "D:/Movie/assas.torrent", "D:/Movie/dir"
		TorrentClient client = new TorrentClient();
		client.start();
		try {
			client.startNewSession("D:/Movie/orig.torrent", "D:/Movie/dir");
			client.startNewSession("D:/Movie/vamp.torrent", "D:/Movie/dir");
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (QueueingException e) {
			_logger.debug("{}", e.getMessage());
		}
	}


}
