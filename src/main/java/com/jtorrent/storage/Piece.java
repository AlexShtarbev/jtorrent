package com.jtorrent.storage;

import java.nio.ByteBuffer;

/**
 * <p>
 * Piece is a torrent piece. Each piece consists of a number of block that are
 * exchanged in via the peer message protocol. The torrent file specifies the
 * piece length, however, the last piece may be of a smaller size.
 * </p>
 * 
 * <p>
 * The Piece object also needs to be comparable. This is so, because the algorithm
 * employed for selecting a piece is Rarest First. The algorithm states that the 
 * pieces with the smallest frequency are downloaded first and the ones with a 
 * higher one - later. In order to know the frequency of a piece among the peer set,
 * the peer has a frequency counter and that count is used for comparing pieces when
 * the rarest piece is to be chosen. 
 * </p>
 * 
 * <p>
 * <b>NOTE:</b> pieces may span file boundaries when the torrent has multiple
 * files.
 * </p>
 * 
 * @see <a href=
 *      "https://wiki.theory.org/BitTorrentSpecification#piece:_.3Clen.3D0009.2BX.3E.3Cid.3D7.3E.3Cindex.3E.3Cbegin.3E.3Cblock.3E">Piece
 *      structure</a>
 * @author Alex
 *
 */
public class Piece implements Comparable<Piece>{
	public static final int HASH_SIZE = 20;

	private final int _index;
	private final long _begin;
	private final long _size;
	private ByteBuffer _data;
	private long _remaining;

	/**
	 * Each piece has a hash that is extracted from the meta info file.
	 */
	private final byte[] _hash;

	/**
	 * Boolean variable that signifies is the piece has been saved on disk.
	 */
	private volatile boolean _onDisk;
	/**
	 * Counts the number of peers that have this piece. This is used for the Rarest First
	 * algorithm which uses the number of peers that have this piece. With the counter
	 * we keep track of how rare, or how easily available, a piece is.
	 */
	private int _frequency;

	public Piece(int index, long begin, long size, byte[] hash) {
		_index = index;
		_begin = begin;
		_size = size;
		_hash = hash;

		_onDisk = false;
		_frequency = 0;
	}

	public int getIndex() {
		return _index;
	}

	public long getBegin() {
		return _begin;
	}

	public long getSize() {
		return _size;
	}

	/**
	 * More or less this method is to be called when a have message is received
	 * so that the counter can be updated.
	 */
	public void have() {
		_frequency++;
	}

	/**
	 * A peer no longer has this piece and so the have counter is decremented.
	 */
	public void lost() {
		_frequency--;
	}

	/**
	 * 
	 * @return <b>true</b> - if at least one peer has this piece; <b>false</b> -
	 *         no peers have this piece.
	 */
	private boolean isAvailable() {
		return _frequency > 0;
	}
	
	public int getFrequency() {
		return _frequency;
	}

	public boolean isOnDisk() {
		return _onDisk;
	}

	public void setOnDisk(boolean onDisk) {
		_onDisk = onDisk;
	}
	
	public synchronized boolean hasBlock(int blockBegin, byte[] block) {
		if(_data == null) {
			return false;
		}
		byte[] data = _data.array();
		for(int i = 0; i < block.length; i++) {
			if(data[i + blockBegin] == block[i]) {
				return true;
			}
		}
		
		return false;
	}

	public synchronized void addBlock(ByteBuffer block, int blockBegin) {
		// The allocation of the piece is lazily declared this way
		// so that the hash memory is not filled with the millions
		// of pieces that need to be downloaded. Thus, when a piece
		// has been chosen to be downloaded from a peer, its buffer is
		// initialized and its blocks are added as they receive from the
		// remote peer.
		// NOTE: When a peer disconnects, the piece whose blocks it was sending
		// to the torrent client, will be annulled. In this case, the next peer
		// that will transmit this piece's data will start sending from block
		// 0 again. When this happens, all of the piece's data needs to be
		// discarded and a new byte buffer allocated for the peer. The described 
		// approach is valid as blocks are sent sequentially in increasing number.
		if (_data == null /*|| blockBegin == 0*/) {
			_data = ByteBuffer.allocate((int) _size);
			_remaining = _size;
		}
		
		// Mark he amount of bytes that need to be written to complete the
		// piece.
		block.rewind();
		_remaining -= block.remaining();
		// Position the buffer to where the block starts.
		_data.position(blockBegin);
		_data.put(block);
		_data.rewind();
	}

	/**
	 * 
	 * @return <b>true</b> if the piece has received all its blocks;
	 *         <b>false</b> - if it still needs to receive more blocks.
	 */
	public boolean isComplete() {
		return _remaining == 0;
	}
	
	public long getRemaining() {
		return _remaining;
	}

	public ByteBuffer getData() {
		return _data.duplicate();
	}

	public byte[] getHash() {
		return _hash.clone();
	}

	public void releaseData() {
		_data = null;
	}

	@Override
	public int compareTo(Piece piece) {
		if (_index == piece.getIndex()) {
			return 0;
		}
		// Compare frequency.
		if(_frequency != piece.getFrequency()) {
			return _frequency < piece.getFrequency() ? -1 : 1;
		}
		// If their frequency is equal, the chose the piece with the smaller index.
		return _index < piece.getIndex() ? -1 : 1;		
	}
}
