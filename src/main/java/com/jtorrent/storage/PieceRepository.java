package com.jtorrent.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jtorrent.messaging.message.RequestMessage;
import com.jtorrent.metainfo.InfoDictionary;
import com.jtorrent.metainfo.MetaInfo;
import com.jtorrent.peer.Peer;
import com.jtorrent.torrent.SessionInfo;
import com.jtorrent.torrent.TorrentSession;

/**
 * An abstraction over the piece storage. It provides functions for reading and
 * writing blocks of data to disk.
 * 
 * 
 * Each peer that wants to download or seed pieces from a torrent need to register 
 * with the repository by calling {@link #register(Peer)} method. When the peer is
 * no longer connected and communicating with the client, the peer must call the
 * {@link #unregitser(Peer)} method.
 * <p>
 * </p>
 * @author Alex
 *
 */
public class PieceRepository {

	private static final double END_GAME_PERCENT = 0.95;
	
	private static final Logger _logger = LoggerFactory.getLogger(PieceRepository.class);

	private Piece[] _pieces;
	private final FileStore _fileStore;
	private final SessionInfo _sessionInfo;
	private final InfoDictionary _infoDict;

	private BitSet _completedPieces;
	/**
	 * Pieces that are currently being downloaded or requested.
	 */
	private BitSet _inFlightPieces;
	private Map<String, RequestedPiece> _requestedPiecesMap;
	
	// Rarest first variables.
	private Map<String, BitSet> _peerBitSetMap;
	private SortedSet<Piece> _rarestSet;
	private RarestFirstSelector _pieceSelector;
	
	public PieceRepository(TorrentSession session) {
		_fileStore = session.getFileStore();
		_sessionInfo = session.getSessionInfo();
		_infoDict = session.getMetaInfo().getInfoDictionary();

		// Calculate the number of pieces that the client needs to download.
		// NOTE: here the last pieces, which is not equal to the standard
		// piece size is also factored in.
		int numPieces = (int) (Math.ceil((double) _fileStore.size() / _infoDict.getPieceLength()));
		_pieces = new Piece[numPieces];
		_completedPieces = new BitSet(numPieces);
		_inFlightPieces = new BitSet(numPieces);
		_requestedPiecesMap = new HashMap<>();
		
		// Add the hashes from the .torrent meta info file into every piece
		// object. This way we can later check if the pieces have been
		// downloaded successfully.
		addPieces(_infoDict.getPieces().duplicate(), _infoDict);
		
		// Keep track of which peer has which pieces.
		_rarestSet = Collections.synchronizedSortedSet(new TreeSet<Piece>());
		_peerBitSetMap = new HashMap<String, BitSet>();
		_pieceSelector = new RarestFirstSelector();
	}
	
	private void addPieces(ByteBuffer hashes, InfoDictionary info) {
		hashes.clear();
		for (int index = 0; index < _pieces.length; index++) {
			byte[] hash = new byte[Piece.HASH_SIZE];
			hashes.get(hash);

			long begin = ((long)index) * info.getPieceLength();
			// The last piece of the torrent may not be equal to the pieceLength
			// specified
			// in the .torrent file. In that case the last piece has a length of
			// [total files size] - [last piece begin]. We can get the total
			// file size
			// from the file store and the begin offset is easily calculate
			// above.
			int size = (int) Math.min(info.getPieceLength(), _fileStore.size() - begin);
			Piece newPiece = new Piece(index, begin, size, hash);
			_pieces[index] = newPiece;
		}
	}

	public Piece get(int index) {
		if(index < 0 || index >= size()) {
			return null;
		}
		
		return _pieces[index];
	}

	public Piece[] toPieceArray() {
		return _pieces;
	}

	/**
	 * Every piece consists of a blocks. This function tries to read a block of
	 * data. It checks the block boundaries and whether the piece is on disk.
	 * 
	 * @param pieceIndex
	 *            The index of the piece.
	 * @param blockBegin
	 *            Where the block starts in the piece.
	 * @param blockSize
	 *            The size of the block that is to be read.
	 * @return A ByteBuffer containing the data that was read.
	 * @throws IllegalStateException
	 *             If the piece is not currently on disk
	 * @throws IOException
	 *             When I/O exception occurs during reading.
	 */
	public ByteBuffer readBlock(int pieceIndex, int blockBegin, int blockSize)
			throws IllegalStateException, IOException {
		Piece piece = _pieces[pieceIndex];
		if (!piece.isOnDisk()) {
			throw new IllegalStateException("Trying to read from a piece that is not on disk.");
		}

		if (blockBegin + blockSize > piece.getSize()) {
			throw new IllegalArgumentException("Attempting to read beyond peice#" + piece.getIndex() + " boundaries.");
		}

		ByteBuffer block = ByteBuffer.allocate(blockSize);
		// The torrent files are viewed as a linear space. The block has an
		// offset in the
		// piece itself and the piece has an offset from the beginning of the
		// entire file
		// space. Thus, the block begins at [piece begin offset] + [block begin
		// offset].
		int read = _fileStore.read(block, piece.getBegin() + blockBegin);
		// We need to rewind buffer so that the calling function will start
		// reading from
		// its beginning.
		block.rewind();
		block.limit(read >= 0 ? read : 0);
		return block;
	}

	/**
	 * Write a block to a piece.
	 * 
	 * @param pieceIndex
	 *            The index of the piece.
	 * @param block
	 *            The block of data that is to be added to the piece
	 * @param blockBegin
	 *            Where the block begins in the piece
	 * @throws IOException
	 *             When I/O exception occurs when writing to disk.
	 */
	public synchronized void writeBlock(int pieceIndex, ByteBuffer block, int blockBegin) throws IOException, IllegalStateException {
		Piece piece = _pieces[pieceIndex];
		if (block == null) {
			throw new IllegalArgumentException("Cannot write null data to piece #" + piece.getIndex());
		}
		
		piece.addBlock(block, blockBegin);
		// Only when the piece has had all it's blocks added, can it be stored
		// on disk.
		_logger.debug("[BLOCK]Piece {} has {} remaining", pieceIndex, piece.getRemaining());
		if (piece.isComplete()) {
			try {
				_fileStore.write(piece.getData(), piece.getBegin());
				_inFlightPieces.set(pieceIndex, false);
				
				if(!check(piece.getIndex())) {
					piece.clear();
					throw new IllegalStateException("received piece #" + pieceIndex + " is not valid");
				} else {				
					markPieceComplete(pieceIndex);
					// The data is released after it has been written to disk. This
					// way
					// the immediate heap can be garbage collected to free memory.
					piece.releaseData();
					_logger.info("{}% complete", completedPercent());
				}
			} catch (IOException e) {
				_logger.warn("could not write piece #" + pieceIndex + " to disk.");
				throw e;
			}
		}
	}

	/**
	 * 
	 * @param pieceIndex
	 *            The index of the piece.
	 * @return <b>true</b> - if the piece is on disk;<b>false</b>
	 * @throws IOException
	 */
	public synchronized boolean check(int pieceIndex) throws IOException {
		Piece piece = _pieces[pieceIndex];
		// Read the data from disk.
		ByteBuffer pieceData = ByteBuffer.allocate((int) piece.getSize());
		_fileStore.read(pieceData, piece.getBegin());
		pieceData.rewind();
		// Put the data in a byte array so that we can do comparison.
		byte[] byteData = new byte[(int) piece.getSize()];
		pieceData.get(byteData);

		// Hash the data from disk and check if it corresponds to the piece hash
		// read
		// from the meta info file.
		boolean res = false;
		try {
			MessageDigest encrypted = MessageDigest.getInstance(MetaInfo.HASHING_ALGORITHM);
			encrypted.reset();
			encrypted.update(byteData);
			byte[] digest = encrypted.digest();
			res = Arrays.equals(digest, piece.getHash());
		} catch (NoSuchAlgorithmException e) {
			res =  false;
		}
		
		return res;
	}

	public synchronized boolean checkPieceHasBlock(int pieceIndex, int blockBegin) {
		return _pieces[pieceIndex].hasBlock(blockBegin);
	}
	
	public int size() {
		return _pieces.length;
	}
	
	public synchronized boolean isPieceToBeDownloaded(int index) {
		return !_completedPieces.get(index) && !_inFlightPieces.get(index);
	}

	/**
	 * Marks the piece identified by its index as complete.
	 * 
	 * @param pieceIndex
	 *            The index of the piece.
	 */
	public synchronized void markPieceComplete(int pieceIndex) {
		_logger.debug("HURRRRAHHHH!!! Piece {} completed", pieceIndex);
		// Check of the piece has not already been completed.
		// If so - do nothing.
		if(_completedPieces.get(pieceIndex)) {
			return;
		}
		Piece piece = _pieces[pieceIndex];
		piece.setOnDisk(true);
		_completedPieces.set(pieceIndex);
		_sessionInfo.setDownloaded(_sessionInfo.getDownloaded() + _pieces[pieceIndex].getSize());
		_sessionInfo.setLeft(_sessionInfo.getLeft() - _pieces[pieceIndex].getSize());
	}

	/**
	 * 
	 * @return The number of pieces successfully downloaded/written on disk.
	 */
	public synchronized int getNumberOfcompletedPieces() {
		return _completedPieces.cardinality();
	}

	public synchronized double completedPercent() {
		double percent = ((double) _completedPieces.cardinality() / _pieces.length) * 100;
		return percent;
	}

	/////////////////////////// Choosing a piece for download ///////////////////////////
	
	/**
	 * Registers a peer with the PieceRepository. Thus the repository will save and monitor
	 * the current piece that the peer is downloading or sending and will also provide it
	 * with which piece it can download next.
	 * @param peer
	 */
	public synchronized void register(Peer peer) {
		_peerBitSetMap.put(peer.getHexPeerID(), new BitSet(_pieces.length));
	}
	
	public synchronized void unregister(Peer peer) {
		_peerBitSetMap.remove(peer.getHexPeerID(), new BitSet(_pieces.length));
	}
	
	/**
	 * The peer repository starts to record the pieces available at the peer.
	 * @param peer The peer to follow.
	 * @param bitSet The initial bit set that is to be bound to the peer.
	 */
	public synchronized void followPeer(Peer peer, BitSet bitSet) {		
		_logger.debug("followed peer {}", peer.getHostAddress());
		
		BitSet bitField = _peerBitSetMap.get(peer.getHexPeerID());
		if(bitField == null){
			register(peer);
			bitField = _peerBitSetMap.get(peer.getHexPeerID());
		}
		bitField.or(bitSet);
		
		_peerBitSetMap.put(peer.getHexPeerID(), bitField);
		
		for(int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i + 1)) {
			updatePieceFrequency(i, true);
		}
	}
	
	/**
	 * The repository no longer keeps tabs on the peer's bit set, requested pieces
	 * and in flight blocks.
	 * @param peer The peer to unfollow.
	 */
	public synchronized void unfollowPeer(Peer peer) {
		_logger.debug("unfollowed peer {}", peer.getHostAddress());
		BitSet bitSet = _peerBitSetMap.get(peer.getHexPeerID());
		if(bitSet != null) {
			for(int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i + 1)) {
				updatePieceFrequency(i, false);
			}
		}
		
		RequestedPiece reqPiece = _requestedPiecesMap.remove(peer.getHexPeerID());
		if(reqPiece != null) {
			_inFlightPieces.set(reqPiece.getPiece().getIndex(), false);
			// Release the data from the requested piece if the peer started downloading a piece.
			reqPiece.getPiece().releaseData();
		}
	}
	
	///////////////////////// PIECE /////////////////////////
	
	public void removeCurrentRequestedPiece(Peer peer) {
		synchronized (peer) {
			// Check if a requested piece exist first.
			RequestedPiece rp = _requestedPiecesMap.get(peer.getHexPeerID());
			if(rp == null) {
				return;
			}
			
			_requestedPiecesMap.put(peer.getHexPeerID(), null);
		}
	}
	
	/**
	 * Updates the bit set of a peer by setting the piece index in the set to <b>true</b>.
	 * @param peer The peer whose bit set is to be updated.
	 * @param index The index of the piece in the bit set
	 */
	public void setPeerHavePiece(Peer peer, int index, boolean have) {
		synchronized (peer) {
			updatePieceFrequency(index, have);
			BitSet pieceSet = _peerBitSetMap.get(peer.getHexPeerID());
			pieceSet.set(index);
			_peerBitSetMap.put(peer.getHexPeerID(), pieceSet);
		}
	}
	
	/**
	 * Updates the frequency of the piece.
	 * @param index The piece whose frequency is to be updates.
	 * @param available Whether the piece is available or has been lost due to peer disconnecting. 
	 */
	private synchronized void updatePieceFrequency(int index, boolean available) {
		Piece piece = _pieces[index];
		_rarestSet.remove(piece);
		// update the piece frequency
		if(available) {
			piece.have();
		} else {
			piece.lost();
		}
		_rarestSet.add(piece);
	}
	
	/**
	 * Determines which next piece the Peer should start downloading.
	 * @param peer The peer whose next piece is to be chosen.
	 */
	public synchronized Piece selectNextPiece(Peer peer) throws IllegalStateException {
		Piece piece = null;
		synchronized(peer) {
			RequestedPiece requestedPiece = _requestedPiecesMap.get(peer.getHexPeerID());
			if(requestedPiece != null) {
				throw new IllegalStateException("Peer #" + peer.getHostAddress() + " has piece in flight.");
			}
			
			piece = _pieceSelector.select(peer);
			if(piece == null) {
				return null;
			}
			_inFlightPieces.set(piece.getIndex());
			requestedPiece = new RequestedPiece(piece);
			_requestedPiecesMap.put(peer.getHexPeerID(), requestedPiece);
		}
		
		_logger.debug("chose piece {} for peer {}", piece.getIndex(), peer.getHostAddress());
		
		return piece;
	}
	
	public Piece getDownloadingPiece(Peer peer) {
		synchronized (peer) {
			RequestedPiece rp = _requestedPiecesMap.get(peer.getHexPeerID());
			if(rp == null) {
				return null;
			}
			
			return rp.getPiece();
		}
	}
	
	public synchronized BitSet getCompletedPieces() {
		return _completedPieces;
	}
	
	/**
	 * 
	 * @param peer The peer which will be inspected and from which it will be deduced
	 * if any pieces can be downloaded.
	 * @return A BitSet object which contains which pieces can be downloaded from
	 * this peer.
	 */
	public synchronized BitSet provideDownloadablePieces(Peer peer) {
		BitSet freePieceSet = (BitSet) _peerBitSetMap.get(peer.getHexPeerID()).clone();
		freePieceSet.andNot(_completedPieces);
		freePieceSet.andNot(_inFlightPieces);
		return freePieceSet;
	}
	
	public synchronized boolean isRepositoryCompleted() {
		return _completedPieces.cardinality() == _pieces.length;
	}
	
	///////////////////////// BLOCK
	
	public boolean isDownloadingPiece(Peer peer) {
		synchronized (peer) {
			return _requestedPiecesMap.get(peer.getHexPeerID()) != null;
		}
	}
	
	/**
	 * Returns a list of block to be sent for downloading.
	 * @param peer The peer which requests the blocks.
	 * @return A list of blocks that are to be downloaded.
	 */
	public LinkedBlockingQueue<ByteBuffer> requestBlocks(Peer peer) {
		synchronized (peer) {
			RequestedPiece rp = _requestedPiecesMap.get(peer.getHexPeerID());
			if(rp == null) {
				return null;
			}
			
			return rp.provideBlocks();
		}
	}
	
	public void markBlockCompleted(Peer peer, int blockBegin) {
		synchronized (peer) {
			RequestedPiece rp = _requestedPiecesMap.get(peer.getHexPeerID());
			if(rp == null) {
				return;
			}
			
			rp.blockCompleted(blockBegin);
		}
	}
	
	public BlockingQueue<Block> getBlocksInFlight(Peer peer) {
		synchronized (peer) {
			RequestedPiece rp = _requestedPiecesMap.get(peer.getHexPeerID());
			if(rp == null) {
				return null;
			}
			return rp.getBlocksInFlight();
		}
	}
	
	public void cancelAllRequestedBlocks(Peer peer) {
		synchronized (peer) {
			RequestedPiece rp = _requestedPiecesMap.get(peer.getHexPeerID());
			if(rp == null) {
				return;
			}
			
			_requestedPiecesMap.put(peer.getHexPeerID(), null);
		}
	}
	
	public synchronized boolean hasReachedEndgame() {
		return !(_completedPieces.cardinality() < END_GAME_PERCENT * _pieces.length);
	}
	
	///////////////////////// REQUESTED PIECE
	
	private static class RequestedPiece {
		private static final int REQUESTED_BLOCKS_QUEUE_SIZE = 5;
		
		private final Piece _piece;
		private int _lastBlock;
		
		private BlockingQueue<Block> _blocksInFlight;

		public RequestedPiece(Piece piece) {
			_piece = piece;
			_blocksInFlight = new LinkedBlockingQueue<Block>(REQUESTED_BLOCKS_QUEUE_SIZE);
		}
		
		public Piece getPiece() {
			return _piece;
		}
		
		public LinkedBlockingQueue<ByteBuffer> provideBlocks() {
			if(_blocksInFlight.size() == REQUESTED_BLOCKS_QUEUE_SIZE) {
				return null;
			}
			
			LinkedBlockingQueue<ByteBuffer> messages = new LinkedBlockingQueue<>();
			while(_blocksInFlight.remainingCapacity() > 0 && _lastBlock < _piece.getSize()) {
				int length = (int) Math.min((int)_piece.getSize() - _lastBlock,
						RequestMessage.DEFAULT_REQUEST_SIZE);
				ByteBuffer msg = RequestMessage.make(_piece.getIndex(), _lastBlock, length);
				messages.add(msg);
				_blocksInFlight.add(new Block(_piece.getIndex(), _lastBlock, length));
				_lastBlock += length;
			}
			_logger.debug("Sending {} blocks ({} last) with {} remaining block for piece {}({})", messages.size(), _lastBlock, _blocksInFlight.size(), _piece.getIndex(), _piece.getSize());
			return messages;
		}
		
		public void blockCompleted(int blockBegin) {
			if(_blocksInFlight == null) {
				return;
			}
			
			for(Block block : _blocksInFlight) {
				if(block.getPieceIndex() == _piece.getIndex() && block.getBegin() == blockBegin) {
					_blocksInFlight.remove(block);
					break;
				}
			}
			
			_logger.debug("Completed block # {} - blocks in flight for piece {} are {}", blockBegin, _piece.getIndex(), _blocksInFlight.size());
		}
		
		public BlockingQueue<Block> getBlocksInFlight() {
			return _blocksInFlight;
		}
	}
	
	public static class Block {
		private final int _pieceIndex;
		private final int _begin;
		private final int _length;
		
		public Block(int index, int begin, int length) {
			_pieceIndex = index;
			_begin = begin;
			_length = length;
		}

		public int getLength() {
			return _length;
		}

		public int getBegin() {
			return _begin;
		}

		public int getPieceIndex() {
			return _pieceIndex;
		}
	}
	
	///////////////////////// PIECE SELECTION 
	
	/**
	 * <p>
	 * The rarest first algorithm works as follows. Each peer maintains a list of the number 
	 * of copies of each piece in its peer set. It uses this information to define a rarest
	 * pieces set. Let m be the number of copies of the rarest piece, then the index of each
	 * piece with m copies in the peer set is added to the rarest pieces set. The rarest
	 * pieces set of a peer is updated each time a copy of a piece is added to or removed 
	 * from its peer set. Each peer selects the next piece to download at random in its 
	 * rarest pieces set.
	 * </p>
	 * 
	 * <p>
	 * RarestFirstSelector does the job of selecting a a random piece among the first rarest
	 * pieces. The number of rare pieces is defined in <b>MAX_SET_SIZE</b>.
	 * </p>
	 * @author Alex
	 *
	 */
	private class RarestFirstSelector {
		
		public static final int MAX_SET_SIZE = 40;
		private Random _generator;
		
		public RarestFirstSelector() {
			_generator = new Random(System.currentTimeMillis());
		}
		
		public Piece select(Peer peer) {
			// We can determine if a peer has a free piece if that piece has not
			// been downloaded yet or is not currently being downloaded.
			// The following code clears all the bits that are completed and 
			// currently in flight, leaving those available for download.
			BitSet freePieceSet = (BitSet) _peerBitSetMap.get(peer.getHexPeerID()).clone();
			freePieceSet.andNot(_completedPieces);
			freePieceSet.andNot(_inFlightPieces);
			// If all the pieces are either completed or in flight the it is likely
			// that end game is reached. For this reason the in-flight pieces are not 
			// included in calculating the free piece set and the same pieces can be
			// requested by different peers.
			if (freePieceSet.cardinality() == 0) {
				freePieceSet = (BitSet) _peerBitSetMap.get(peer.getHexPeerID()).clone();
				freePieceSet.andNot(_completedPieces);
				if (!hasFreePieces(peer, freePieceSet)) {
					_logger.debug("No piece found for peer {}", peer.getHostAddress());
					return null;
				}
			}

			return selectRarest(freePieceSet);
		}
		
		/**
		 * Checks if there are any pieces left to be downloaded. If not - then 
		 * it should be checked if end game mode has been reached. If so - then
		 * it will return <b>true</b>. Otherwise - no free pieces left.
		 * @param freePieceSet
		 */
		private boolean hasFreePieces(Peer peer, BitSet freePieceSet) {
			// Check if there are any free bits. If not - check for end game.
			
			// This means there are no pieces to download for the peer and
			// so there is nothing to do here.
			if(freePieceSet.cardinality() == 0) {
				return false;
			}
			
			// Check if the torrent session has not reached a state of entering
			// end game.
			if(!hasReachedEndgame()) {
				_logger.debug("[END GAME]Not reached end game");
				return false;
			} else {
				_logger.debug("[END GAME]Reached end game");
			}
			
			return true;
		}
		
		/**
		 * The pieces are sorted by rarity(frequency). Among the first MAX_SET_SIZE a rondom
		 * piece is selected.
		 * @param freePieceSet
		 * @return A randomly selected rare piece.
		 */
		private Piece selectRarest(BitSet freePieceSet) {
			ArrayList<Piece> rarestFirstSubList = new ArrayList<Piece>(MAX_SET_SIZE);
			synchronized (_rarestSet) {
				for (Piece piece : _rarestSet) {
					if (freePieceSet.get(piece.getIndex())) {
						rarestFirstSubList.add(piece);
						if (rarestFirstSubList.size() >= MAX_SET_SIZE) {
							break;
						}
					}
				}
			}
			
			if (rarestFirstSubList.size() == 0) {
				return null;
			}

			return rarestFirstSubList.get(_generator.nextInt(
					Math.min(rarestFirstSubList.size(), MAX_SET_SIZE)));
		}
	}
}
