package com.jtorrent.peer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jtorrent.messaging.message.BitfieldMessage;
import com.jtorrent.messaging.message.CancelMessage;
import com.jtorrent.messaging.message.ChokeMessage;
import com.jtorrent.messaging.message.HaveMessage;
import com.jtorrent.messaging.message.InterestedMessage;
import com.jtorrent.messaging.message.Message;
import com.jtorrent.messaging.message.MessageExchangeException;
import com.jtorrent.messaging.message.MessageListener;
import com.jtorrent.messaging.message.Messages;
import com.jtorrent.messaging.message.NotInterestedMessage;
import com.jtorrent.messaging.message.PieceMessage;
import com.jtorrent.messaging.message.RequestMessage;
import com.jtorrent.messaging.message.UnchokeMessage;
import com.jtorrent.messaging.rate.RateAccumulator;
import com.jtorrent.storage.Piece;
import com.jtorrent.storage.PieceRepository;
import com.jtorrent.storage.PieceRepository.Block;
import com.jtorrent.torrent.SessionInfo;
import com.jtorrent.torrent.TorrentSession;
import com.jtorrent.utils.Utils;

public class Peer implements MessageListener {
	
	private static final Logger _logger = LoggerFactory.getLogger(Peer.class);

	private InetSocketAddress _address;
	private String _peerID;
	private String _hexPerrID;
	private final String _host;

	private TorrentSession _torrentSession;
	private MessageChannel _messageChannel;
	private Object _messageLock;
	
	// Peer state.
	private boolean _amChoking;
	private boolean _amInterested;
	
	private boolean _peerChoking;
	private boolean _peerInterested;
	
	// Download and Upload rates
	private RateAccumulator _downloadRate;
	private RateAccumulator _uploadRate;
	
	private List<PeerStateListener> _listeners;
	
	public Peer(String host, int port) {
		this(host, port, null);
	}

	public Peer(String host, int port, String peerID) {
		setAddress(new InetSocketAddress(host, port));
		setPeerID(peerID);
		_host = _address.getAddress() + ":" + _address.getPort();
		_messageLock = new Object();
		
		_amChoking = true;
		_peerChoking = true;
		
		_listeners = new ArrayList<PeerStateListener>();
	}

	public Peer(Socket socket, String peerID) {
		this(socket.getInetAddress().getHostAddress(), socket.getPort(), peerID);
	}

	public InetSocketAddress getAddress() {
		return _address;
	}

	public void setAddress(InetSocketAddress address) {
		_address = address;
	}

	public String getPeerID() {
		return _peerID;
	}

	public void setPeerID(String peerID) {
		_peerID = peerID;
		if (peerID != null) {
			_hexPerrID = Utils.convertToHex(_peerID.getBytes());
		}
	}

	public String getIP() {
		return _address.getAddress().getHostAddress();
	}

	public String getHexPeerID() {
		return _hexPerrID;
	}
	
	public void setAmChoking(boolean amChoking) {
		if(_amChoking != amChoking) {
			_amChoking = amChoking;
			_logger.debug("{} peer {}", _amChoking ? "Choking": "Unchoking", getHostAddress());
			_messageChannel.send(_amChoking ? ChokeMessage.make(): UnchokeMessage.make());
		}
				
	}
	
	public boolean getAmChoking() {
		return _amChoking;
	}
	
	public void setAmInterested(boolean amInterested) {
		if(_amInterested != amInterested) {
			_amInterested = amInterested;
			_logger.debug("{} in peer {}", _amInterested ? "Am Interested": "Am not Interested", getHostAddress());
			_messageChannel.send(_amInterested ? InterestedMessage.make() : NotInterestedMessage.make());
		}
	}
	
	public boolean getPeerInterested() {
		return _peerInterested;
	}
	
	public RateAccumulator getDownloadRate() {
		return _downloadRate;
	}
	
	public RateAccumulator getUploadRate() {
		return _uploadRate;
	}
	
	public void addStateListener(PeerStateListener listener) {
		_listeners.add(listener);
	}
	
	public synchronized void notifyAllListeners() {
		for(PeerStateListener listener : _listeners) {
			listener.onPeerDisconnected(this);
		}
	}

	/**
	 * Converts a byte array to a hexadecimal string representation of the data.
	 * 
	 * @param bytes
	 *            The data to convert
	 * @return A hexadecimal representation of the data.
	 */
	

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append(_address.getAddress().getHostAddress());
		sb.append(":" + _address.getPort());
		sb.append(" " + _peerID);

		return sb.toString();
	}

	public String getHostAddress() {
		return _host;
	}
	
	public MessageChannel getMessageChannel() {
		return _messageChannel;
	}
	
	public synchronized void bind(TorrentSession torrentSession, SocketChannel socketChannel) throws IOException {
		_torrentSession = torrentSession;
		unbind(true);
		_messageChannel = MessageChannel.open(socketChannel, _torrentSession, this);
		_messageChannel.addMessageListener(this);
		PieceRepository repo = _torrentSession.getPieceRepository();

		// If there is anything from the file on disk - send it to the peer.
		if(repo.getNumberOfcompletedPieces() > 0) {
			ByteBuffer bitField = BitfieldMessage.make(repo.getCompletedPieces(), repo.size());
			_logger.debug("Peer {} sending {} bytes BITFIELD message", getHostAddress(), bitField.capacity());
			_messageChannel.send(bitField);
		}
		
		_downloadRate = new RateAccumulator();
		_downloadRate.reset();
		
		_uploadRate = new RateAccumulator();
		_downloadRate.reset();
	}

	public synchronized boolean isConnected() {
		return _messageChannel != null && _messageChannel.isConnected();
	}
	
	public synchronized void unbind(boolean shouldCancelBlocks){
		_logger.debug("Peer {} unbinding", getHostAddress());
		PieceRepository repo = _torrentSession.getPieceRepository();
		repo.unfollowPeer(this);
		repo.unregister(this);
		
		notifyAllListeners();
		
		if(shouldCancelBlocks && isConnected()) {			
			cancelAllRequests();
			_messageChannel.send(NotInterestedMessage.make());
		}
	
		if(_messageChannel != null) {
			_logger.debug("Peer {} closed its message channel", getHostAddress());
			_messageChannel.close();
			_messageChannel = null;
		}		
	}
	
	public void cancelAllRequests() {
		PieceRepository repo = _torrentSession.getPieceRepository();
		if(repo.isDownloadingPiece(this)) {
			BlockingQueue<Block> blocksInFlight = repo.getBlocksInFlight(this);
			
			if(blocksInFlight == null) {
				return;
			}
			
			for(Block block : blocksInFlight) {
				ByteBuffer cancelBlockMessage = CancelMessage.make(
						block.getPieceIndex(), block.getBegin(), block.getLength());
				_messageChannel.send(cancelBlockMessage);
			}
			
			repo.cancelAllRequestedBlocks(this);
		}
	}
	
	public void cancelPiece(int pieceIndex, int blockBegin, ByteBuffer block) {
		PieceRepository repo = _torrentSession.getPieceRepository();
		if(repo.isDownloadingPiece(this)) {
			// Form the cancel message
			block.rewind();
			byte[] bb = new byte[block.remaining()];
			block.get(bb);
			ByteBuffer msg = CancelMessage.make(pieceIndex, blockBegin, bb.length);
			
			// Send the cancel message to the other peers.
			// NOTE: it is important to get the connected peers.
			Set<Peer> peers = _torrentSession.getPeerManager().getConnectedPeers();			
			for(Peer peer : peers) {
				if(!peer.getHexPeerID().equals(getHexPeerID())) {
					peer.getMessageChannel().send(msg);
					repo.markBlockCompleted(peer, blockBegin);
				}
			}
		}
	}

	@Override
	public void onMessageChannelException(Exception e) {
		_logger.debug("Peer {} received exception from message channel: {}", getHostAddress(), e.getMessage());
		unbind(true);
	}

	@SuppressWarnings("incomplete-switch")
	@Override
	public void onMessageReceived(ByteBuffer message) {
		if(_torrentSession.isStopped()) {
			return;
		}
		message.rewind();
		PieceRepository repo = _torrentSession.getPieceRepository();
		try {
			Message msg = Messages.parse(_torrentSession, message.duplicate());
			_logger.debug("Peer {} received {} message...", getHostAddress(), msg.getMessageType().toString());
			switch(msg.getMessageType()) {
			case KEEP_ALIVE:
				// Do nothing.
				break;
			case CHOKE:
				onChoke(repo);
				break;
			case UNCHOKE:
				onUnchoke(repo);
				break;
			case INTERESTED:
				onInterested();
				break;
			case NOT_INTERESTED:
				onNotInterested();
				break;
			case HAVE:
				onHave(repo, msg);
				break;
			case BITFIELD:
				onBitfield(repo, msg);
				break;
			case REQUEST:
				onRequest(repo, msg);
				break;
			case PIECE:
				onPiece(repo, msg);
				break;
			}
		} catch (MessageExchangeException e) {
			_logger.debug("received Exception {} from peer {}", e.getMessage(), getHexPeerID());
		}
		
	}
	
	private void onChoke(PieceRepository repo) {
		_logger.debug("received CHOKE from peer {}", getHostAddress());
		synchronized (_messageLock) {
			_peerChoking = true;
			Piece piece = repo.getDownloadingPiece(this);
			if(piece != null) {
				repo.setPeerHavePiece(this, piece.getIndex(), false);
				cancelAllRequests();
			}
		}
	}
	
	private void onUnchoke(PieceRepository repo) {
		_logger.debug("received UNCHOKE from peer {}", getHostAddress());
		synchronized (_messageLock) {			
			_peerChoking = false;
			askForNewPiece(repo);
		}		
	}

	private void askForNewPiece(PieceRepository repo) {
		_logger.debug("Peer {} asking for new piece", getHostAddress());
		Piece piece = repo.selectNextPiece(this);
		if(piece != null) {
			sendBlockRequests(repo);
		}
	}
	
	private void sendBlockRequests(PieceRepository repo) {
		LinkedBlockingQueue<ByteBuffer> blockRequests = repo.requestBlocks(this);
		if(blockRequests == null) {
			return;
		}
		for(ByteBuffer msg : blockRequests) {
			_messageChannel.send(msg);
		}
	}
	
	private void onInterested() {
		_logger.debug("received INTERESTED from peer {}", getHostAddress());
		synchronized (_messageLock) {
			_peerInterested = true;
		}
	}
	
	private void onNotInterested() {
		_logger.debug("received NOT_INTERESTED from peer {}", getHostAddress());
		synchronized (_messageLock) {
			_peerInterested = false;
		}
	}
	
	private void onHave(PieceRepository repo, Message msg) {
		_logger.debug("received HAVE from peer {}", getHostAddress());
		synchronized (_messageLock) {
			HaveMessage haveMessage = (HaveMessage)msg;
			Piece piece = repo.get(haveMessage.getPieceIndex());
			
			// If the client is not downloading the piece or if it dies not have it
			// downloaded, then the client should start communicating with the peer
			// by becoming interested in it.
			if(repo.isPieceToBeDownloaded(piece.getIndex())) {
				setAmInterested(true);
			}
			
			// Update the piece repository.
			repo.setPeerHavePiece(this, haveMessage.getPieceIndex(), true);
			// If the peer has not started downloading a piece - ask for one.
			// Some peers send an incomplete bit field and the follow it up
			// with have messages. This is why it is a good idea to ask for
			// a new piece, if possible, when a HAVE message arrives.
			if(!_peerChoking && _amInterested && !repo.isDownloadingPiece(this)) {
				askForNewPiece(repo);
			}			
		}	
	}
	
	private void onBitfield(PieceRepository repo, Message msg) {
		_logger.debug("received BITFIELD from peer {}", getHostAddress());
		synchronized (_messageLock) {			
			BitfieldMessage bitfieldMessage = (BitfieldMessage) msg;
			repo.followPeer(this, bitfieldMessage.getBitField());
			
			// If the peer has pieces that the client can download,
			// the the client is interested in the peer. Otherwise-
			// it is not.
			_logger.debug("Peer {} has {} available pieces.", getHostAddress(), repo.provideDownloadablePieces(this).cardinality());
			if (repo.provideDownloadablePieces(this).cardinality() == 0) {
				setAmInterested(false);
			} else {
				setAmInterested(true);
			}
		}
	}
	
	private void onPiece(PieceRepository repo, Message msg) {		
		PieceMessage pieceMessage = (PieceMessage) msg;
		Piece piece = repo.get(pieceMessage.getPieceIndex());
		repo.markBlockCompleted(this, pieceMessage.getBegin());
		// Update the downloaded data rate.
		_downloadRate.accumulate(pieceMessage.getBlock().capacity());
			
		synchronized (_messageLock) {
			if(piece.isOnDisk()) {
				// If the piece is already on disk request to download a new one
				// by removing the current requested piece in the repository and
				// all the existing block requests.
				onPieceComplete(repo, piece);
			} else {
				try {
					if(repo.hasReachedEndgame()) {
						handleEndgameBlock(repo, pieceMessage);
					} else {
						repo.writeBlock(piece.getIndex(), pieceMessage.getBlock(),
								pieceMessage.getBegin());
					}
					
					if(piece.isComplete()) {
						onPieceComplete(repo, piece);
					} else {
						// The piece has not been completed. Ask for a new piece.
						_logger.debug("Received block {}; asking for more blocks from peer {}...", pieceMessage.getBegin(), getHostAddress());
						sendBlockRequests(repo);
					}
					// Check if the torrent has been completely downloaded.
					if(repo.size() == repo.getNumberOfcompletedPieces() &&
							!_torrentSession.isFinilizing()) {
						_torrentSession.onTorrentDownloaded(repo);
					}
				} catch (IOException | IllegalStateException e) {
					_logger.warn("{} in peer {}", e, getHexPeerID());
				}
			}
		}
	}
	
	private void handleEndgameBlock(PieceRepository repo, PieceMessage pieceMessage) throws IllegalStateException, IOException {
		Piece piece = repo.get(pieceMessage.getPieceIndex());
		if(repo.checkPieceHasBlock(piece.getIndex(), pieceMessage.getBegin(),
				pieceMessage.getBlock().duplicate())) {
			_logger.debug("Piece {} already has block {}", piece.getIndex(),
					pieceMessage.getBegin());
		} else {
			repo.writeBlock(piece.getIndex(), pieceMessage.getBlock(),
					pieceMessage.getBegin());
			
			// Cancel the piece form the rest of the peers.
			cancelPiece(piece.getIndex(), pieceMessage.getBegin(),
					pieceMessage.getBlock().duplicate());
		}
	}
	
	private synchronized void onPieceComplete(PieceRepository repo, Piece piece) {
		// Send a HAVE message to all other peers.
		_logger.debug("completed piece {} for peer {}", piece.getIndex(), getHostAddress());
		
		ByteBuffer haveMessage = HaveMessage.make(piece.getIndex());
		PeerManager peerManager = _torrentSession.getPeerManager();
		for(Peer peer: peerManager.getConnectedPeers()) {
			peer.getMessageChannel().send(haveMessage);
		}
		// Ask for the next piece after this one has been completed.
		repo.removeCurrentRequestedPiece(this);
		askForNewPiece(repo);
	}
	
	private synchronized void onRequest(PieceRepository repo, Message msg) {
		// We should watch put for choked peers who send requests. The BitTorrent protocol
		// states that in this case all of the peers requests are to be dropped. Same goes
		// for the occasion when the client is asked for a piece it does not have.
		if(_amChoking) {
			_logger.warn("Peer {} is CHOKED by client. Dropping request...", getHostAddress());
			unbind(true);
			return;
		}
		
		RequestMessage reqMsg = (RequestMessage) msg;
		
		// Check if the torrent has the piece from which the peer wants to read.
		Piece piece = repo.get(reqMsg.getPieceIndex());
		if(piece == null || !piece.isOnDisk()) {
			_logger.warn("Peer {} is asking for a piece {} that the client does not have", getHostAddress(),
					reqMsg.getPieceIndex());
			unbind(true);
			return;
		}
		
		// Check the block size.
		if(reqMsg.getLength() > RequestMessage.MAX_REQUEST_SIZE) {
			_logger.warn("Peer {} requested a block too large", getHostAddress());
			unbind(true);
			return;
		}
		
		// All the checks are OK. Sent a PIECE message to the peer.
		sendPieceResponse(repo, reqMsg);
	}
	
	private synchronized void sendPieceResponse(PieceRepository repo, RequestMessage reqMsg) {
		try {
			ByteBuffer block = repo.readBlock(reqMsg.getPieceIndex(), reqMsg.getBegin(), reqMsg.getLength());
			ByteBuffer pieceMessage = PieceMessage.make(reqMsg.getPieceIndex(), reqMsg.getBegin(), block);
			_messageChannel.send(pieceMessage);
			
			_uploadRate.accumulate(block.capacity());
			SessionInfo sessionInfo = _torrentSession.getSessionInfo();
			sessionInfo.setUploaded(sessionInfo.getDownloaded() 
					+ repo.get(reqMsg.getPieceIndex()).getSize());
		} catch (IllegalStateException e) {
			_logger.warn("Peer {} has illegal state {}", getHostAddress(), e.getMessage());
		} catch (IOException e) {
			_logger.warn("IO exception while sending block to peer {}: {}", getHostAddress(), e.getMessage());
		}
	}
}
