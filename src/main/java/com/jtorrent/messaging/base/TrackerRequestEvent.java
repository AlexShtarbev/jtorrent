package com.jtorrent.messaging.base;

/**
 * <p>
 * The sequence and numbering of the events is chosen to follow the
 * <a href="http://www.bittorrent.org/beps/bep_0015.html">BEP #15</a> UDP extension.
 * <br/>
 * The events correspond to the same ones used for sending HTTP GET requests to
 * HTTP/HTTPS trackers, so for ease of use the UDP event ordering is chosen.
 * <br/>
 * </p>
 * <p>
 * The client sends a STARTED message only once when it wants to receive the list
 * of peers from the torrent tracker.When the download is complete the client sends a 
 * COMPLETED event to the tracker. Naturally a STOPPED event is sent when the announcing is
 * to be stopped completely.<br/>
 * It should be noted, however, that during the period when the STARTED and COMPLETED/STOPPED
 * events the client should send periodic NONE messages, thus helping the tracker to keep
 * up-to-date information on the clients involved in downloading the torrent.
 * </p>  
 * @author Alex
 *
 */
public enum TrackerRequestEvent {
	NONE(0),
	COMPLETED(1),
	STARTED(2),
	STOPPED(3);
	
	private final int _eventID;
	
	private TrackerRequestEvent(int id) {
		_eventID = id;
	}
	
	public int getID() {
		return _eventID;
	}
	
	public String eventName() {
		return name().toLowerCase();
	}
	
	public static TrackerRequestEvent findByName(String name){
		for(TrackerRequestEvent tre : values()) {
			if(tre.eventName().equals(name.toLowerCase())) {
				return tre;
			}
		}
		return null;
	}
	
	public static TrackerRequestEvent findByID(int eventID){
		for(TrackerRequestEvent tre : values()) {
			if(tre.getID() == eventID) {
				return tre;
			}
		}
		return null;
	}
	
}
