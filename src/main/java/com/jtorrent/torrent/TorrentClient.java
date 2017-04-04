package com.jtorrent.torrent;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jtorrent.messaging.announce.ConnectionService;
import com.jtorrent.peer.Peer;
import com.jtorrent.torrent.restore.RestoreManager;

/**
 * <p>
 * A torrent client class that is responsible for starting, stopping and resuming torrent
 * sessions.
 * </p>
 * <p>
 * It also queues torrents when the maximum number of downloading torrents is exceeded.
 * When a torrent finishes downloading, the first torrent session from the queue is retrieved
 * and promptly started.
 * </p>
 */
public class TorrentClient implements TorrentSessionEventListener {
	public static final String BITTORRENT_ID_PREFIX = "-TO0042-";
	
	private static final String RESTORE_FILE = "torrents.json";
	
	public static final int MAX_DOWNLOADING_TORRENTS = 5;
	public static final int MAX_TORRENTS = 30;
	
	private ConnectionService _connectionService;
	private ExecutorService _sessionExecutor;
	
	private List<TorrentSessionTask> _torrentQueue;
	private List<TorrentSession> _activeSessions;
	private int _downloading;
	private Peer _clientPeer;
	
	private final RestoreManager _restoreManager;

	// see https://logback.qos.ch/manual/introduction.html
	private static final Logger _logger = LoggerFactory.getLogger(TorrentClient.class);

	public TorrentClient() throws IllegalStateException {
		_sessionExecutor = Executors.newCachedThreadPool();
		
		_connectionService = new ConnectionService();		
		String id = BITTORRENT_ID_PREFIX + UUID.randomUUID().toString().split("-")[4];
		try {
			_connectionService.setClientPeerID(new String(id.getBytes(TorrentSession.BYTE_ENCODING)));
		} catch (UnsupportedEncodingException e) {
			_logger.warn("Exception occured while setting client id : {}", e.getMessage());
		}
				
		_torrentQueue = new LinkedList<TorrentSessionTask>();
		_activeSessions = new LinkedList<TorrentSession>();
		
		try {
			_restoreManager = new RestoreManager(RESTORE_FILE);
		} catch (Exception e) {
			throw new IllegalStateException("Unable to restore torrents: " + e.getMessage());
		}
		
	}
	
	public RestoreManager getRestoreManager() {
		return _restoreManager;
	}
	
	public ConnectionService getConnectionService() {
		return _connectionService;
	}
	
	public Peer getClientPeer() {
		return _clientPeer;
	}
	
	public void start() {
		_connectionService.start();
		_clientPeer = new Peer(
				_connectionService.getSocketAddress().getAddress().getHostAddress(),
				_connectionService.getSocketAddress().getPort(),
				_connectionService.getClientPeerID());
	}
	
	public void stop() {
		_connectionService.stop();
		try {
			_connectionService.cancel();
		} catch (IOException e) {
			// Ignore
		}
		for(TorrentSession session : _activeSessions) {
			if(!session.isStopped() && !session.isQueuing()) {
				session.stop(false);
			}
		}
		_sessionExecutor.shutdownNow();
	}	

	public synchronized TorrentSession startNewSession(String fileName, String destination)
			throws Exception {
		TorrentSessionTask task = new TorrentSessionTask(fileName, destination);
		startTask(task);
		return task.getTorrentSession();
	}
	
	public synchronized void startNewSession(TorrentSession session)
			throws Exception {
		TorrentSessionTask task = new TorrentSessionTask(session);
		startTask(task);
	}
	
	private synchronized void startTask(TorrentSessionTask task) throws Exception {
		if(_activeSessions.size() + _torrentQueue.size() == MAX_TORRENTS) {
			throw new QueueingException("Reach max number of torrents.");
		}
		_restoreManager.appendTorrentSession(task.getTorrentSession());
		if(_downloading == MAX_DOWNLOADING_TORRENTS) {
			_torrentQueue.add(task);
		} else {
			_downloading++;
			_sessionExecutor.execute(task);
		}
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

	public synchronized void resumeTorrentSession(TorrentSession session) {
		if(session.isQueuing()) {
			return;
		}
		
		_sessionExecutor.execute(new ResumeTorrentTask(session));
	}
	
	public synchronized void stopTorrentSession(TorrentSession session) {
		if(session.isQueuing()) {
			return;
		}
		
		session.stop(false);
	}
	
	public synchronized void removeTorrentSession(TorrentSession session) {
		try {
			_restoreManager.removeTorrentSessionRestorePoint(session);
		} catch (Exception e) {
			_logger.warn("Could not remove resotre point for {}: e", session.getTorrentFileName(),
					e.getMessage());
		}
		if(session.isQueuing()) {
			for(TorrentSessionTask task : _torrentQueue) {
				if(task.getTorrentSession().equals(session)) {
					_torrentQueue.remove(task);
					return;
				}
			}
		}
		
		_activeSessions.remove(session);
		session.stop(true);
	}
	
	public void shutdown() {
		for(TorrentSession session : _activeSessions) {
			session.stop(true);
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
	
	private class TorrentSessionTask implements Runnable {
		private final TorrentSession _torrentSession;
		
		public TorrentSessionTask(TorrentSession torrentSession) {
			_torrentSession = torrentSession;
		}
		
		public TorrentSessionTask(String fileName, String destination) 
				throws NoSuchAlgorithmException, InvalidAlgorithmParameterException, IOException, URISyntaxException {
			_torrentSession = new TorrentSession(fileName, destination, _clientPeer, _connectionService);
		}
		
		public TorrentSession getTorrentSession() {
			return _torrentSession;
		}

		@Override
		public void run() {
			_connectionService.register(_torrentSession);
			addNewActiveSession(_torrentSession);
			_torrentSession.register(TorrentClient.this);
			_torrentSession.start(); 
			_logger.debug("Exited TorrentSessionTask for torrent {}", _torrentSession.getTorrentFileName());
		}
	}
	
	private class ResumeTorrentTask implements Runnable {
		private final TorrentSession _torrentSession;
		
		public ResumeTorrentTask(TorrentSession torrentSession) {
			_torrentSession = torrentSession;
		}
		
		@Override
		public void run() {
			_torrentSession.start();
		}
	}
		
	// FIXME - remove
	public static void main(String[] args) {
		// Restore the previous sessions when the client is started.
//		try {
//			List<TorrentSession> sessions = _restoreManager.restroreTorrentSessions(_connectionService, _clientPeer);
//			for(TorrentSession session: sessions) {
//				startNewSession(session);
//			}
//		} catch (Exception e) {
//			throw new IllegalStateException("Unable to seatore torrent sessions: " + e.getMessage());
//		}
 		
		// Future<Boolean> result2 = es.submit(new SessionTask(clientPeer,
		// "D:/Movie/t1.torrent", "D:/Movie/dir"));
		// "D:/Movie/assas.torrent", "D:/Movie/dir"
		TorrentClient client = new TorrentClient();
		client.start();
		try {
			//client.startNewSession("D:/Movie/orig.torrent", "D:/Movie/dir");
			//client.startNewSession("D:/Movie/vamp.torrent", "D:/Movie/dir");
			client.startNewSession("D:/Movie/Nie, nashite i vashite S01E08.torrent", "D:/Movie/dir");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
