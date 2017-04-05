package com.jtorrent.torrent;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jtorrent.messaging.announce.AnnounceException;
import com.jtorrent.messaging.announce.AnnounceService;
import com.jtorrent.messaging.announce.ConnectionService;
import com.jtorrent.messaging.announce.TrackerResponseMessage;
import com.jtorrent.metainfo.MetaInfo;
import com.jtorrent.peer.Peer;
import com.jtorrent.peer.PeerManager;
import com.jtorrent.storage.FileStore;
import com.jtorrent.storage.MultiFileStore;
import com.jtorrent.storage.Piece;
import com.jtorrent.storage.PieceRepository;

public class TorrentSession {
	public enum Status {
		QUEUING,
		DOWNLOADING,
		FINILIZING,
		SEEDING,
		CHECKING,
		STOPPED,
	}
	
	public static final String BYTE_ENCODING = "ISO-8859-1";
	public static final Status INITIAL_STATUS = Status.QUEUING;

	private static final Logger _logger = LoggerFactory.getLogger(TorrentSession.class);
	
	private final String _torrentfileName;
	private final String _destinationFolder;

	private final MetaInfo _metaInfo;
	private final SessionInfo _sessionInfo;
	private final AnnounceService _announceService;
	private final PeerManager _peerManager;
	private final ConnectionService _connectionService;
	private final FileStore _store;
	private final PieceRepository _pieceRepository;
	private Status _torrentStatus;
	
	private List<TorrentSessionEventListener> _listeners;
	
	private long _checkedPieces;
	
	public TorrentSession(String torrentFileName, String destination, Peer clientPeer,
			ConnectionService connectionService, Status status) 
			throws NoSuchAlgorithmException, InvalidAlgorithmParameterException, IOException, URISyntaxException {
		this(torrentFileName, destination, clientPeer, connectionService);
		_torrentStatus = status;
	}

	public TorrentSession(String torrentFileName, String destination, Peer clientPeer,
			ConnectionService connectionService)
			throws NoSuchAlgorithmException, InvalidAlgorithmParameterException, IOException, URISyntaxException {
		_torrentfileName = torrentFileName;
		_destinationFolder = destination;
		
		_metaInfo = new MetaInfo(new File(torrentFileName));
		_store = new MultiFileStore(_metaInfo.getInfoDictionary().getFiles(), destination);
		_torrentStatus = INITIAL_STATUS;
		
		// Session information
		_sessionInfo = new SessionInfo(clientPeer);
		_sessionInfo.setLeft(_store.size());

		// Announcing and connection management
		_announceService = new AnnounceService(this);
		_connectionService = connectionService;
		_peerManager = new PeerManager(connectionService, this);

		// Pieces handling
		_pieceRepository = new PieceRepository(this);
		_listeners = new LinkedList<TorrentSessionEventListener>();
	}
	
	protected void check() {
		_torrentStatus = Status.CHECKING;
		checkTorrentCompletion();
	}

	protected void start() {		
		// Firstly, set the status of the torrent.
		if(!_pieceRepository.isRepositoryCompleted()) {			
			_torrentStatus = Status.DOWNLOADING;
		} else {
			notifyDownloadCompleted();
			startSeeding();
		}
		
		// Start the services.
		_announceService.start();
		try {
			_peerManager.start();
		} catch (Exception e) {
			_logger.warn("expcetion occurred while starting torrent session: {}", e.getMessage());
		}
	}

	protected void stop(boolean isRemoved) {
		_torrentStatus = Status.STOPPED;
		// Make disconnecting happen in the background.
		Thread th = new Thread(new Runnable() {
			
			@Override
			public void run() {
				try {
					_announceService.stop(true);
					_peerManager.disconnectAllConcurrently();
					_peerManager.stop();
					if(isRemoved) {
						_peerManager.cleanup();
					}
				} catch (InterruptedException e) {
					// Ignore.
				}
				if(isRemoved) {
					_peerManager.cleanup();
				}
				_connectionService.unregister(TorrentSession.this);			
			}
		});
		th.setDaemon(true);
		th.start();		
	}

	/**
	 * <p>
	 * Handles the periodic announce and update response to the tracker.
	 * </p>
	 * <p>
	 * <b>NOTE:</b> This is the response sent from the tracker when the client
	 * sends its START announce requests and then the following update messages
	 * and their responses.
	 * </p>
	 * 
	 * @param message
	 *            The response message from the tracker.
	 */
	public void onTrackerResponse(TrackerResponseMessage message) {
		if(!Status.SEEDING.equals(_torrentStatus)) {
			_peerManager.registerConnectionAll(message.getPeers());
		}
	}

	public String getTorrentFileName() {
		return _torrentfileName;
	}
	
	public String getDestionationFolder() {
		return _destinationFolder;
	}
	
	/**
	 * 
	 * @return The metainfo information from the .torrent file.
	 */
	public MetaInfo getMetaInfo() {
		return _metaInfo;
	}

	public SessionInfo getSessionInfo() {
		return _sessionInfo;
	}

	public PeerManager getPeerManager() {
		return _peerManager;
	}

	public ConnectionService getConnectionService() {
		return _connectionService;
	}

	public FileStore getFileStore() {
		return _store;
	}
	
	public List<String> getFileNames() {
		return _store.getFileNames();
	}

	public PieceRepository getPieceRepository() {
		return _pieceRepository;
	}
	
	public void setStatus(Status status) {
		_torrentStatus = status;
	}
	
	public Status getStatus() {
		return _torrentStatus;
	}
	
	public AnnounceService getAnnounceService() {
		return _announceService;
	}
	
	public boolean isQueuing() {
		return Status.QUEUING.equals(_torrentStatus);
	}
	
	public boolean isChecking() {
		return Status.CHECKING.equals(_torrentStatus);
	}
	
	public boolean isDownloading() {
		return Status.DOWNLOADING.equals(_torrentStatus);
	}

	public boolean isFinilizing() {
		return Status.FINILIZING.equals(_torrentStatus);
	}
	
	public boolean isSeeding() {
		return Status.SEEDING.equals(_torrentStatus);
	}
	
	public boolean isStopped() {
		return Status.STOPPED.equals(_torrentStatus);
	}
	
	
	public synchronized void register(TorrentSessionEventListener listsner) {
		_listeners.add(listsner);
	}
	
	public void notifyDownloadCompleted() {
		for(TorrentSessionEventListener listener : _listeners) {
			listener.onSessionClosed();
		}
	}
	
	public double getCheckedPiecesPrgress() {
		return ((double)_checkedPieces) / ((double)_pieceRepository.size());
	}
	
	/**
	 * Check how much of the torrent is present on disk.
	 */
	private void checkTorrentCompletion() {
		ExecutorService exec = Executors.newCachedThreadPool();

		int percent = 0;
		List<Future<Boolean>> results = new ArrayList<Future<Boolean>>();
		// In order not to overwhelm the program with threads, each few iterations
		// the results from tasks are inspected and then the algorithm moves on
		// with scheduling more checker tasks.
		for (Piece p : _pieceRepository.toPieceArray()) {
			if(isStopped()) {
				results.clear();
				exec.shutdownNow();
				return;
			}
			results.add(exec.submit(new PieceChecker(p)));

			if (results.size() != Runtime.getRuntime().availableProcessors()) {
				continue;
			}

			try {
				percent = handleFinishedChekers(results, percent);
			} catch (Exception e) {
				_logger.debug("Could not retrieve the check result:" + e.getCause());
			}

			results.clear();
		}
		
		// Handle any checkers left unfinished.
		try {
			percent = handleFinishedChekers(results, percent);
		} catch (Exception e) {
			_logger.debug("Could not retrieve the check result:" + e.getCause());
		}

		_logger.info("Have: " + _pieceRepository.completedPercent() + "%");
		_logger.info("Have {}/{}", _pieceRepository.getCompletedPieces().cardinality(), _pieceRepository.size());
		exec.shutdown();
	}
	
	private int handleFinishedChekers(List<Future<Boolean>> results, int percent) throws Exception {
		for (Future<Boolean> result : results) {
			try {
				// Wait for the check to complete.
				result.get();
				_checkedPieces++;
				double checkedPercent = ((double) _checkedPieces / _pieceRepository.size()) * 100;
				if(checkedPercent > percent) {
					_logger.info("checked " + percent + "%");
					percent += 10;
				}
				
			} catch (Exception e) {
				throw e;
			}
		}
		
		return percent;
	}

	/**
	 * A callable task that checks if a piece is present on disk.
	 * 
	 * @author Alex
	 *
	 */
	private class PieceChecker implements Callable<Boolean> {

		private final Piece _piece;

		public PieceChecker(Piece piece) {
			_piece = piece;
		}

		@Override
		public Boolean call() {
			// The check function reads from disk and compares the hash of the
			// written piece with the one in the repository. If the piece is 
			// not on disk, the hashes will obviously not match. If they do - 
			// then the torrent is on disk.
			boolean presentOnDisk;
			try {
				presentOnDisk = _pieceRepository.check(_piece.getIndex());
			} catch (IOException e) {
				_logger.warn("Could not read from piece {} from piece repository.", _piece.getIndex());
				return false;
			}
			if (!presentOnDisk){
				return false;
			}
			
			_pieceRepository.markPieceComplete(_piece.getIndex());
			_logger.debug("have piece {}", _piece.getIndex());
			return true;			
		}
	}
	
	public void onTorrentDownloaded(PieceRepository repo) {
		_logger.info("Last piece received and checked. Torrent has been downloaded");
		
		try {
			_torrentStatus = Status.FINILIZING;
			getFileStore().complete();
			getFileStore().close();
			_announceService.sendCompletedMessage();
		} catch (IOException e) {
			_logger.warn("could not finish downloading the file {}", e.getMessage());
			return;
		} catch (AnnounceException e) {
			_logger.warn("could not send COMPLEDTED message to tracker");
		}
		
		// Notify the listeners that the download was completed.
		notifyDownloadCompleted();
		
		startSeeding();
	}
	
	private void startSeeding() {
		if(isSeeding()) {
			return;
		}
		
		_torrentStatus = Status.SEEDING;
	}
}
