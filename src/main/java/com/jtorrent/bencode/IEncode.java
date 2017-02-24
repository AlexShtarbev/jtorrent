package com.jtorrent.bencode;

import java.io.IOException;
import java.io.OutputStream;

public interface IEncode {
	
	/**
	 * Decodes the data in the BObject and writes it to the output stream.
	 * @param o The object which is to be encoded.
	 * @param out The output stream to which the encoded value is to be written.
	 * @return
	 * @throws IOException 
	 */
	public void encode(Object o, OutputStream out) throws IOException;
}
