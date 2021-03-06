package com.jtorrent.metainfo;

import java.nio.ByteBuffer;
import java.nio.channels.IllegalSelectorException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.jtorrent.bencode.BObject;
import com.jtorrent.bencode.BObject.BEncodingException;
import com.jtorrent.storage.Piece;

public class InfoDictionary {
	public static final String NAME_KEY = "name";
	public static final String PIECE_LENGTH_KEY = "piece length";
	public static final String PIECES_KEY = "pieces";
	public static final String FILES_KEY = "files";
	public static final String FILE_KEY = "file";

	// File related fields.
	private final String _name;
	private final int _pieceLength;
	private final ByteBuffer _pieces;
	private final long _length;

	private final List<FileDictionary> _files;

	public InfoDictionary(Map<String, BObject> metaInfo) throws BEncodingException, IllegalStateException {
		_name = metaInfo.get(NAME_KEY).asString();
		_pieceLength = metaInfo.get(PIECE_LENGTH_KEY).asInt();
		_pieces = ByteBuffer.wrap(metaInfo.get(PIECES_KEY).asBytes());

		// Check if the info is in single or multiple file mode.
		if (metaInfo.containsKey(FILES_KEY)) {
			_files = FileDictionary.fromFiles(_name, metaInfo.get(FILES_KEY).asList());
			_length = calculateFilesLength();
		} else {
			_files = new ArrayList<FileDictionary>();
			_length = metaInfo.get(FileDictionary.LENGHT_KEY).asLong();
			_files.add(new FileDictionary(_name, _length));
		}

		if ((long) _pieces.capacity() / Piece.HASH_SIZE * _pieceLength < _length) {
			throw new IllegalStateException("Peices and files length do not match in size.");
		}
	}

	private long calculateFilesLength() {
		long length = 0;
		for (FileDictionary file : _files) {
			length += file.getLength();
		}

		return length;
	}

	public String getName() {
		return _name;
	}

	public ByteBuffer getPieces() {
		return _pieces;
	}

	public int getPieceLength() {
		return _pieceLength;
	}

	public long getLength() {
		return _length;
	}

	public List<FileDictionary> getFiles() {
		return _files;
	}
}
