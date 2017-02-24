package com.jtorrent.bencode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.jtorrent.bencode.BObject.BEncodingException;

public class BByteStringCoder implements IDecode, IEncode {
	private static final char DELIMETER = ':';
	
	private static BByteStringCoder _instance;
	
	private BByteStringCoder(){};
	
	public static BByteStringCoder instance() {
		if(_instance == null) {
			_instance = new BByteStringCoder();
		}
		
		return _instance;
	}

	public BObject decode(InputStream in) throws IOException {		
		int length = extractLength(in);
		if(length == -1) {
			return null;
		}
		
		byte[] byteSting = readByteString(in, length);
		if (byteSting == null) {
			return null;
		}
		
		return new BObject(byteSting);
	}

	private int extractLength(InputStream in) throws IOException {
		int length = 0;
		int ch = in.read();
		if(ch == -1){
			return ch;
		}
		if(ch < '0' || ch > '9') {
			throw new BObject.BEncodingException("expected number, got " + (char)ch);
		}
		
		while(ch >= '0' && ch <= '9') {
			length = length*10 +  ch - '0';
			ch = in.read();
		}
		
		if((char)ch != ':') {
			throw new BObject.BEncodingException("expected ':', got " + (char)ch);
		}
		
		return length;
	}
	
	private byte[] readByteString(InputStream in, int length) throws IOException {
		byte[] bytes = new byte[length];

		int bytesRead = 0;
		while (bytesRead < length)
		{
			// Since there might be less bytes read than the originally
			// intended, the input stream is examined until the needed
			// number of bytes is read.
			int i = in.read(bytes, bytesRead, length - bytesRead);
			if (i == -1) {
				return null;
			}
			bytesRead += i;
		}

		return bytes;
	}
	
	public void encode(Object o, OutputStream out) throws IOException {
		if(!(o instanceof String)) {
			throw new BObject.BEncodingException("encode: BByteStringCoder expected string, got " + o.getClass().getName());
		}
		String byteString = (String) o;
		String l = Integer.toString(byteString.length());
		out.write(l.getBytes(BObject.BYTE_ENCODING));
		out.write(DELIMETER);
		out.write(byteString.getBytes());
	}

	public static void main(String[] args) {
		BByteStringCoder bsb = BByteStringCoder.instance();
		try {
			BObject bo = bsb.decode(new ByteArrayInputStream(new String("11:hello world").getBytes()));
			if(bo != null) {
				System.out.println(bo.asString());
			} else {
				System.out.println("wrong format\n");
			}
			ByteArrayOutputStream baout = new ByteArrayOutputStream();
			bsb.encode(bo.asString(), baout);
			System.out.println(baout.toString());
			baout.close();
		} catch (BEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
