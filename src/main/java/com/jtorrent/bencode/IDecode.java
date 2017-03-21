package com.jtorrent.bencode;

import java.io.IOException;
import java.io.InputStream;

public interface IDecode {

	/**
	 * Decodes the data in the input stream and returns an object containing the
	 * decoded value.
	 * 
	 * @param in
	 *            The input stream from which the data is decoded.
	 * @return BObject representation of the decoded value.
	 * @throws IOException
	 */
	public BObject decode(InputStream in) throws IOException;
}
