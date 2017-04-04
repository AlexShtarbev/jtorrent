package com.jtorrent.storage;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.jtorrent.metainfo.FileDictionary;

/**
 * MultiFileStore is used for working with multiple files (file stores).
 * <p>
 * The BitTorrent file specification allows for multiple files to be downloaded.
 * The entire store is viewed as a continuous file storage on which it can be
 * operated. This class is an abstraction that allows for easily working with
 * multiple files.
 * </p>
 * 
 * @author Alex
 *
 */
public class MultiFileStore implements FileStore {

	private List<FileStore> _fileStores;
	/**
	 * Contains the beginning offsets of every file store. This provides the
	 * abstraction that this file store is viewed as one big continuous storage.
	 */
	private Map<FileStore, Long> _beginMap;
	/**
	 * The aggregate size of the files.
	 */
	private long _size;
	
	private final List<String> _fileNames;

	public MultiFileStore(List<FileDictionary> files, String parentDir) throws IOException {
		if (parentDir == null || !(new File(parentDir)).isDirectory()) {
			throw new IllegalArgumentException("Incorrect parent directory.");
		}

		_fileStores = new ArrayList<FileStore>();
		_beginMap = new HashMap<FileStore, Long>();
		_fileNames = new LinkedList<String>();
		_size = 0;
		for (FileDictionary file : files) {
			File actual = new File(parentDir, file.getFile().getPath());
			// Create the directory of the file.
			actual.getParentFile().mkdirs();
			
			SingleFileStore store = new SingleFileStore(actual, file.getLength());
			_fileStores.add(store);
			_fileNames.add(actual.getName());
			_beginMap.put(store, _size);
			_size += file.getLength();
		}
	}
	
	public String getParentName() {
		if(_fileNames == null || _fileNames.isEmpty()) {
			return null;
		}
		
		String parent = null;
		for(FileStore _store : _fileStores) {
			String currentParent = _store.getParentName();
			if(parent == null || currentParent.length() < parent.length()) {
				parent = currentParent;
			}
		}
		
		return parent;
	}

	public long getSize() {
		return _size;
	}

	public List<String> getFileNames() {
		return _fileNames;
	}
	
	@Override
	public int read(ByteBuffer data, long begin) throws IOException {
		int size = data.remaining();
		int read = 0;

		List<FileChunk> chunks = provideConcernedFiles(begin, size);

		for (FileChunk chunk : chunks) {
			data.limit((int) (read + chunk.size()));
			read += chunk.getFileStore().read(data, chunk.begin());
		}

		// Check if we have read the needed amount of data.
		if (read < size) {
			throw new IOException("Not enough data read: expected " + size + ", read " + read);
		}

		return read;
	}

	@Override
	public int write(ByteBuffer data, long begin) throws IOException {
		int size = data.remaining();
		int written = 0;

		List<FileChunk> chunks = provideConcernedFiles(begin, size);

		for (FileChunk chunk : chunks) {
			data.limit((int) (written + chunk.size()));
			written += chunk.getFileStore().write(data, chunk.begin());
		}

		// Check if we have read the needed amount of data.
		if (written < size) {
			throw new IOException("Not enough data read: expected " + size + ", read " + written);
		}

		return written;
	}

	@Override
	public void close() throws IOException {
		for (FileStore store : _fileStores) {
			store.close();
		}
	}

	@Override
	public void complete() throws IOException {
		for (FileStore store : _fileStores) {
			store.complete();
		}
	}

	@Override
	public boolean isComplete() {
		for (FileStore store : _fileStores) {
			if (!store.isComplete()) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Helper class which contains information about a file and from where the
	 * data should be recorded and how much data should be recorded.
	 * 
	 * @author Alex
	 *
	 */
	private static class FileChunk {
		private final FileStore _fileStore;
		private final long _begin;
		private final long _size;

		public FileChunk(FileStore fileStore, long begin, long size) {
			_fileStore = fileStore;
			_begin = begin;
			_size = size;
		}

		public FileStore getFileStore() {
			return _fileStore;
		}

		public long begin() {
			return _begin;
		}

		public long size() {
			return _size;
		}
	}

	/**
	 * When a block is to be read or written, this block might span multiple
	 * files as the entire store is viewed as continuous. Thus, it needs to be
	 * determined which files are concerned by the operation, how much data
	 * needs to be provided from each file and where does the data begin from in
	 * the aforementioned file.
	 * 
	 * @param begin
	 *            Where in the entire file store does the block of data begin.
	 *            Again, is should be noted that this block of data is in the
	 *            view of a continuous file store and not a single file.
	 * @param length
	 *            The length of the block of data that is to be read
	 * @return The files from which data is to be read/written.
	 */
	private List<FileChunk> provideConcernedFiles(long begin, long length) {
		if (begin + length > _size) {
			throw new IllegalArgumentException(
					"Requested data above store limit: store size: " + _size + ", requested: begin = " + begin
							+ " length = " + length + "that stretches beyond the store limits");
		}

		List<FileChunk> concerned = new ArrayList<FileChunk>();
		long bytes = 0L;

		for (FileStore store : _fileStores) {
			long storeBegin = _beginMap.get(store);
			// Find the files that have data between [begin, begin + length]
			if (storeBegin + store.size() < begin) {
				continue;
			}

			if (storeBegin >= begin + length) {
				break;
			}

			// If the block spans multiple files the first files begin offset
			// will be before 'begin' - the offset for the requested block of
			// data.
			// The next block, however, will have started after 'begin'. So, in
			// this case we are starting from position 0 in that file.
			long pos = begin - storeBegin;
			if (pos < 0) {
				pos = 0;
			}

			// The length of the block might be bigger than the size of the
			// file.
			// In that case the block is located between [pos, file size].
			// However if the has more data than the currently requested,
			// the size of the chunk spans from the current offset until the
			// the end of the requested chunk.
			long size = Math.min(store.size() - pos, length - bytes);
			concerned.add(new FileChunk(store, pos, size));
			bytes += size;
		}

		if (concerned.size() == 0 || bytes < length) {
			throw new IllegalStateException("expected " + length + " got " + bytes + " bytes/" + begin);
		}

		return concerned;
	}

	@Override
	public long size() {
		return _size;
	}
}
