package com.jtorrent.messaging.http;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.jtorrent.bencode.BDecoder;
import com.jtorrent.bencode.BObject;
import com.jtorrent.bencode.BObject.BEncodingException;
import com.jtorrent.messaging.AnnounceException;
import com.jtorrent.messaging.base.TrackerRequestMessage;
import com.jtorrent.messaging.base.TrackerResponseMessage;
import com.jtorrent.peer.Peer;
import com.jtorrent.torrent.TorrentSession;

public class HTTPTrackerResponseMessage extends TrackerResponseMessage{
	
	public HTTPTrackerResponseMessage(String failureReason, String warningMessage, int interval, int complete,
			int incomplete, List<Peer> peers) {
		super(failureReason, warningMessage, interval, complete, incomplete, peers);
	}

	public static final int COMPACT_PEER_LIST_VALUE_SIZE = 6;
	
	public static HTTPTrackerResponseMessage parse(ByteBuffer message) throws IOException, AnnounceException {
		BObject decodedMessage = BDecoder.instance().decode(message);
		if(decodedMessage == null) {
			throw new AnnounceException("the tracker response is not in Bencode");
		}
		
		Map<String, BObject> params = decodedMessage.asMap();
		// The interval key is mandatory. Check if it has been provided.
		if(params.get(INTERVAL_KEY) == null) {
			throw new AnnounceException("tracker response message does not contains 'interval'");
		}
				
		return new HTTPTrackerResponseMessage(
				params.getOrDefault(FAILURE_REASON_KEY, new BObject("")).asString(),
				params.getOrDefault(WARNING_MESSAGE_KEY, new BObject("")).asString(),
				params.get(INTERVAL_KEY).asInt(), // mandatory
				params.getOrDefault(COMPLETE_KEY, new BObject(0)).asInt(),
				params.getOrDefault(INCOMPLETE_KEY, new BObject(0)).asInt(), 
				providePeerList(params));
	}

	private static List<Peer> providePeerList(Map<String, BObject> params) throws BEncodingException, UnsupportedEncodingException, UnknownHostException {
		BObject peers = params.get(PEERS_KEY);
		
		List<Peer> peerList = new ArrayList<Peer>();
		if(peers instanceof List) {
			peerList = providePeerListFromList(peers.asList());
		} else {
			peerList = providePeerListFromBytes(peers.asBytes());
		}
		
		return peerList;
	}
	
	/**
	 * The torrent client asks for a compact list, therefore the tracker will send a string.
	 * The string consists of multiples of 6 bytes. First 4 bytes are the IP address and 
	 * last 2 bytes are the port number. All the information is in big endian notation.
	 * @param data
	 * @return
	 * @throws BEncodingException 
	 * @throws UnsupportedEncodingException 
	 * @throws UnknownHostException 
	 * @throws AnnounceException 
	 */
	private static List<Peer> providePeerListFromBytes(byte[] peers) throws BEncodingException, UnsupportedEncodingException, UnknownHostException {
		if(peers.length % COMPACT_PEER_LIST_VALUE_SIZE != 0) {
			throw new BObject.BEncodingException("the message is not a multiple of 6");
		}
		
		List<Peer> peersList = new ArrayList<Peer>();
		ByteBuffer peersBuffer = ByteBuffer.wrap(peers);

		for (int i=0; i < peers.length / 6 ; i++) {
			byte[] hostBytes = new byte[4];
			peersBuffer.get(hostBytes);
			InetAddress host = InetAddress.getByAddress(hostBytes);
			int port =
				(0xFF & (int)peersBuffer.get()) << 8 |
				(0xFF & (int)peersBuffer.get());
			peersList.add(new Peer(host.getHostAddress(), port));
		}
		
		return peersList;
	}

	/**
	 * The values is a list of dictionaries(maps) where each dictionary contains the following keys:
	 * <ul>
	 * 	<li>peer id: peer's self-selected ID, as described above for the tracker request (string)</li>
	 * 	<li>ip: peer's IP address either IPv6 (hexed) or IPv4 (dotted quad) or DNS name (string)</li>
	 * 	<li>port: peer's port number (integer)</li>
	 * </ul>
	 * @return
	 * @throws BEncodingException 
	 */
	public static List<Peer> providePeerListFromList(List<BObject> peers) throws BEncodingException {
		List<Peer> peersList = new ArrayList<Peer>();
		
		for(BObject peer: peers) {
			Map<String, BObject> peerData = peer.asMap();
			
			// Extract the the data in the current peer map.
			String host = peerData.get(TrackerRequestMessage.IP_KEY)
					.asString(TorrentSession.BYTE_ENCODING);
			int port = peerData.get(TrackerRequestMessage.PORT_KEY).asInt();
			String peerID = peerData.get(TrackerRequestMessage.PEER_ID_KEY).asString();
			
			peersList.add(new Peer(host, port, peerID));
		}
		
		return peersList;
	}
	
	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("---- Tracker Response ----\n");
		sb.append("\twarning message: " + getWarningMessage() + "\n");
		sb.append("\tfailure reason: " + getFailureReason() + "\n");
		sb.append("\tinterval: " + getInterval() + "\n");
		sb.append("\tcomplete: " + getComplete() + "\n");
		sb.append("\tincomplete: " + getIncomplete()+ "\n");
		sb.append("\t---- Peer List ----\n");
		for(Peer peer : getPeers()) {
			sb.append("\t\t" + peer.toString() + "\n");
		}
		
		return sb.toString();
	}
}
