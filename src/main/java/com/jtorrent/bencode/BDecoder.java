package com.jtorrent.bencode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import org.apache.commons.io.input.AutoCloseInputStream;

public class BDecoder implements IDecode{
	
	private static BDecoder _instance;
	
	private BDecoder() {}
	
	public static BDecoder instance() {
		if(_instance == null) {
			_instance = new BDecoder();
		}
		
		return _instance;
	}
	
	public BObject decode(ByteBuffer in) throws IOException {
		return decode(new AutoCloseInputStream(
			new ByteArrayInputStream(in.array())));
	}

	public BObject decode(InputStream in) throws IOException {		
		IDecode decoder = null;
		switch(first(in)) {
			case 'i': decoder = BIntegerCoder.instance(); break;
			case 's': decoder = BByteStringCoder.instance(); break;
			case 'l': decoder = BListCoder.instance(); break;
			case 'd': decoder = BMapCoder.instance(); break;
		}
		
		return decoder.decode(in);
	}

	private char first(InputStream in) throws IOException {
		in.mark(1);
		int ch = in.read();
		if(ch == -1) {
			throw new BObject.BEncodingException("cannot decode empty stream");
		}
		in.reset();
		
		// Here we create a 'dummy' verb for the byte string representation.
		if (ch >= '0' & ch <= '9') {
			ch = (int)'s';
		}
		
		return (char)ch;
	}
	
	public boolean isEndSentinel(InputStream in) throws IOException {
		in.mark(1);
		int ch = in.read();
		if((char)ch == 'e') {
			return true;
		}
		in.reset();
		return false;
	}
}
