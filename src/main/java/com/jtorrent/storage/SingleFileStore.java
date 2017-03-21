package com.jtorrent.storage;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jtorrent.metainfo.FileDictionary;

/**
 * SingleFileStore is responsible for a single file to which it will read
 * from/write to.
 * <p>
 * The class is <b>thread-safe</b> as it uses FileChannel for thread-safe
 * read/write operations.
 * </p>
 * 
 * @author Alex
 *
 */
public class SingleFileStore implements FileStore {

	private static final Logger _logger = LoggerFactory.getLogger(SingleFileStore.class);

	/**
	 * The file that is to be managed by the file store.
	 */
	private final File _file;
	private final long _fileSize;

	private RandomAccessFile _randomAccessFile;
	private File _workingFile;

	public SingleFileStore(File file, long size) throws IOException {
		_file = file;
		_fileSize = size;

		File partFile = providePartFile();

		if (partFile.exists() || !_file.exists()) {
			_logger.debug("Working with partial file {}.", partFile.getAbsolutePath());
			_workingFile = partFile;
		} else {
			_logger.debug("Working with existing file {}.", _file.getAbsolutePath());
			_workingFile = _file;
		}

		// Open a random access file
		_randomAccessFile = new RandomAccessFile(_workingFile, "rw");
		_randomAccessFile.setLength(_fileSize);
	}

	@Override
	public int read(ByteBuffer data, long begin) throws IOException {
		int dataSize = data.remaining();
		if (begin + dataSize > _fileSize) {
			throw new IllegalArgumentException("The block of data form the offset exceeds the file size");
		}

		// Read the data from the file and save into the data buffer.
		int bytesRead = _randomAccessFile.getChannel().read(data, begin);
		if (bytesRead < dataSize) {
			throw new IOException("Error reading from file: expected " + dataSize + ", got: " + bytesRead);
		}

		return bytesRead;
	}

	@Override
	public int write(ByteBuffer data, long begin) throws IOException {
		int dataSize = data.remaining();

		if (begin + dataSize > _fileSize) {
			throw new IllegalArgumentException("The block of data form the offset exceeds the file size");
		}

		return _randomAccessFile.getChannel().write(data, begin);
	}

	@Override
	public synchronized void close() throws IOException {
		// Make sure that the all the data is written to permanent storage.
		if (_randomAccessFile.getChannel().isOpen()) {
			_randomAccessFile.getChannel().force(true);
		}

		_randomAccessFile.close();
	}

	@Override
	public synchronized void complete() throws IOException {
		// Make sure that the all the data is written to permanent storage.
		if (_randomAccessFile.getChannel().isOpen()) {
			_randomAccessFile.getChannel().force(true);
		}

		// No need to do any moving since we are working on the end file itself.
		if (isComplete())
			return;

		// Move the file to it's final destination.
		_randomAccessFile.close();
		FileUtils.deleteQuietly(_file);
		FileUtils.moveFile(_workingFile, _file);

		// Switch to working with the fully downloaded file.
		_randomAccessFile = new RandomAccessFile(_file, "rw");
		_randomAccessFile.setLength(_fileSize);
		_workingFile = _file;

		// Remove the partial file.
		FileUtils.deleteQuietly(providePartFile());
	}

	@Override
	public boolean isComplete() {
		return _workingFile.equals(_file);
	}

	/**
	 * @return The .part file. It is created when a download of the file is
	 *         started.
	 */
	private File providePartFile() {
		return new File(_file.getAbsolutePath() + FILE_EXTENSION);
	}

	public long size() {
		return _fileSize;
	}

	public File getFile() {
		return _file;
	}
}
