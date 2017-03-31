package com.jtorrent.messaging.announce;

import java.io.IOException;
import java.util.concurrent.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jtorrent.messaging.common.TierManager;
import com.jtorrent.torrent.TorrentSession;

// FIXME - add class comment
public class AnnounceService {

	private static final Logger _logger = LoggerFactory.getLogger(AnnounceService.class);
	
	private final TorrentSession _session;
	private final TierManager _tierManager;
	private ExecutorService _announceService;
	private BlockingQueue<TrackerRequestEvent> _emergencyQueue;

	/**
	 * Interval in seconds that the client should wait between sending regular
	 * requests to the tracker.
	 */
	private int _trackerInterval;
	private boolean _stop;
	/**
	 * When the service is forcefully stopped - a STOP message needs to be sent
	 * to the tracker.
	 */
	private boolean _hardStop;

	public AnnounceService(TorrentSession session) {
		_session = session;
		_tierManager = new TierManager(session);		
		_emergencyQueue = new LinkedBlockingQueue<TrackerRequestEvent>();
	}

	public TierManager getTierManager() {
		return _tierManager;
	}
	
	public void start() {
		_stop = false;
		_hardStop = false;
		_announceService = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
				new LinkedBlockingQueue<Runnable>());
		if (!_tierManager.getTierList().isEmpty() && (!_announceService.isShutdown() && !_announceService.isTerminated()
				&& ((ThreadPoolExecutor) _announceService).getActiveCount() == 0)) {
			_announceService.execute(new AnnounceTask());
		}
	}

	public void stop(boolean shouldHardStop) throws InterruptedException {
		_stop = true;
		_hardStop = shouldHardStop;
		if (!_announceService.isShutdown() && !_announceService.isTerminated()
				&& ((ThreadPoolExecutor) _announceService).getActiveCount() != 0) {
			_announceService.shutdownNow();
			_announceService.awaitTermination(5, TimeUnit.SECONDS);
			_announceService = null;
		}
	}
	

	public void setInterval(int interval) throws InterruptedException {
		if (interval < 0) {
			stop(true);
		} else {
			setTrackerInterval(interval);
		}
	}

	public int getTrackerInterval() {
		return _trackerInterval;
	}

	public void setTrackerInterval(int trackerInterval) {
		if(_trackerInterval <= 0) {
			try {
				stop(true);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		this._trackerInterval = trackerInterval;
	}
	
	public void sendCompletedMessage() throws AnnounceException, IOException {
		_tierManager.provideTrackerClient().queryTracker(TrackerRequestEvent.COMPLETED);
	}
	
	/**
	 * Used to ask the tracker for an update peer list.
	 * @throws InterruptedException
	 */
	public void sendEmergencyTrackerRequest() throws InterruptedException {
		// If there is already an emergency request, then there is not need to 
		// send more.
		if(!_emergencyQueue.isEmpty()) {
			return;
		}
		_emergencyQueue.put(TrackerRequestEvent.NONE);
	}

	/**
	 * The purpose of this task is to send the initial "started" event so that
	 * the client can receive the list of peers from the .torrent file. After
	 * that it sends periodic messages to keep the tracker updated about the
	 * usage of the torrent. <br/>
	 * The task is active so long as the service is being used by the client.
	 * 
	 * <br/>
	 * If the service is to be stopped with a STOP message then such a message
	 * is sent to the tracker.
	 * 
	 * @author Alex
	 *
	 */
	private class AnnounceTask implements Runnable {

		@Override
		public void run() {
			TrackerRequestEvent trackerEvent = TrackerRequestEvent.STARTED;
			_logger.debug("Announcencing...");
			_trackerInterval = 10;
			while (!_stop) {
				try {
					TrackerResponseMessage response = _tierManager.provideTrackerClient().queryTracker(trackerEvent);
					_tierManager.moveTrackerToFront();
					if (response != null) {
						_logger.debug("Announce to tracker {} received", _tierManager.provideTrackerClient().toString());
						handleResponse(response);
					}
					trackerEvent = TrackerRequestEvent.NONE;
				} catch (AnnounceException e) {
					_tierManager.tryNextTrackerClient();
				} catch (IOException e) {
					_logger.warn("could not send announce: {}", e.getMessage());
				} catch (ResponseException e) {
					_logger.warn("exception while handling announce response: {}", e.getMessage());
				}

				try {
					_emergencyQueue.poll(_trackerInterval * 1000, TimeUnit.MILLISECONDS);
				} catch (InterruptedException e) {
					// Not a problem - the task is not doing anything intensive
					// or has a state worth saving.
				}
			}

			if (_hardStop) {
				sendStopRequest();
			}
		}

		private void handleResponse(TrackerResponseMessage message) throws ResponseException {
			if (message.getFailureReason() != null && !message.getFailureReason().isEmpty()) {
				throw new ResponseException(message.getFailureReason());
			}
			setTrackerInterval(message.getInterval());
			_session.onTrackerResponse(message);
		}

		private void sendStopRequest() {
			if (!_announceService.isShutdown() && !_announceService.isTerminated()) {
				// Send the request after a small delay.
				try {
					TimeUnit.MILLISECONDS.sleep(500);
				} catch (InterruptedException e) {
					// Nothing to do
				}
				try {
					_tierManager.provideTrackerClient().queryTracker(TrackerRequestEvent.STOPPED);
				} catch (AnnounceException e) {
					// TODO log
				} catch (IOException e) {
					// TODO log
				}
			}
		}
	}
}
