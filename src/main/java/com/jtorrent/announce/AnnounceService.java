package com.jtorrent.announce;

import java.util.concurrent.*;

import com.jtorrent.announce.messaging.TrackerRequestEvent;
import com.jtorrent.torrent.TorrentSession;

// FIXME - add class comment
public class AnnounceService {

	private final TorrentSession _session;
	private final TierManager _tierManager;
	ExecutorService _announceService;
	
	/**
	 * Interval in seconds that the client should wait between sending regular requests to the tracker.
	 */
	private int _trackerInteral;
	private boolean _stop;
	private boolean _hardStop;
	
	public AnnounceService(TorrentSession session) {
		_session = session;
		_tierManager = new TierManager(session);
		_announceService = new ThreadPoolExecutor(1, 1,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>());
	}
	
	public void start() {
		_stop = false;
		_hardStop = false;
		if(!_tierManager.getTierList().isEmpty() && (!_announceService.isShutdown() 
				&& !_announceService.isTerminated() 
				&& ((ThreadPoolExecutor)_announceService).getActiveCount() == 0)) {
			_announceService.execute(new AnnounceTask());
		}
	}
	
	public void setInterval(int interval) throws InterruptedException {
		if(interval < 0) {
			stop(true);
		} else {
			_trackerInteral = interval;
		}
	}
	
	public void stop(boolean shouldHardStop) throws InterruptedException {
		_stop = true;
		if(!_announceService.isShutdown() && !_announceService.isTerminated()
				&& ((ThreadPoolExecutor)_announceService).getActiveCount() != 0) {
			_announceService.shutdownNow();
			_tierManager.closeAllConnections();
			_announceService.awaitTermination(3, TimeUnit.SECONDS);
		}
	}
	
	/**
	 * The purpose of this task is to send the initial "started" event so
	 * that the client can receive the list of peers from the .torrent 
	 * file. After that it sends periodic messages to keep the tracker
	 * updated about the usage of the torrent.
	 * <br/>
	 * The task is active so long as the service is being used by the client.
	 * @author Alex
	 *
	 */
	private class AnnounceTask implements Runnable {

		@Override
		public void run() {
			TrackerRequestEvent trackerEvent = TrackerRequestEvent.STARTED;
			while(!_stop) {
				try {
					_tierManager.provideTrackerClient().queryTracker(trackerEvent);
					_tierManager.moveTrackerToFront();
					trackerEvent = TrackerRequestEvent.NONE;
				} catch (AnnounceException e) {
					// TODO log
					_tierManager.tryNextTrackerClient();
				}
				
				try {
					TimeUnit.SECONDS.sleep(1);
				} catch (InterruptedException e) {
					// Not a problem - the task is not doing anything intensive
					// or has a state worth saving.
				}
			}
		}		
	}
}
