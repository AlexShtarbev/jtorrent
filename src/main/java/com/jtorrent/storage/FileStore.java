package com.jtorrent.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * An abstract file store the serves as a layer above standard file operations
 * by exposing methods for easier use of read, write and complete operations for
 * a BitTorrent file.
 * 
 * @author Alex
 *
 */
public interface FileStore {
	public static final String FILE_EXTENSION = ".part";

	/**
	 * Read a block of data from the underlying file.
	 * 
	 * @param data
	 *            A buffer into which the data is to be recorded.
	 * @param begin
	 *            From where in the file to start reading data.
	 * @return The number of bytes that the were read from the file store.
	 * @throws IOException
	 *             When and I/O exception occurs while reading.
	 */
	public int read(ByteBuffer data, long begin) throws IOException;

	/**
	 * Write a block of data to the underlying file.
	 * 
	 * @param data
	 *            The block of data that is to be written to the underlying
	 *            file.
	 * @param begin
	 *            Where in the file should the block be put.
	 * @return The number of bytes written in the store.
	 * @throws IOException
	 *             When and I/O exception occurs while writing.
	 */
	public int write(ByteBuffer data, long begin) throws IOException;

	/**
	 * Closes the file store.
	 * 
	 * @throws IOException
	 *             If the underlying file system failed to close.
	 */
	public void close() throws IOException;

	/**
	 * The file store completes the download of the file by moving it from
	 * temporary storage to the final destination.
	 * 
	 * @throws IOException
	 */
	public void complete() throws IOException;

	/**
	 * 
	 * @return <b>true</b> if the file allocated to the file store has been
	 *         downloaded and <b>false</b> - otherwise
	 */
	public boolean isComplete();

	public List<String> getFileNames();
	
	public long size();
}
