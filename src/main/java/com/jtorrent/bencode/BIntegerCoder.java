package com.jtorrent.bencode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;

import com.jtorrent.bencode.BObject.BEncodingException;

public class BIntegerCoder implements IDecode, IEncode {
	/**
	 * We do not encode.decode more than a 255 byte BigInteger.
	 */
	private static final int MAX_SIZE = 255;

	private static BIntegerCoder _instance;

	private BIntegerCoder() {
	};

	public static BIntegerCoder instance() {
		if (_instance == null) {
			_instance = new BIntegerCoder();
		}

		return _instance;
	}

	public BObject decode(InputStream in) throws IOException {
		int ch = in.read();
		if (ch != 'i') {
			throw new BObject.BEncodingException("expected 'i', got " + (char) ch);
		}

		try {
			if (isZero(in)) {
				return new BObject(BigInteger.ZERO);
			}
		} catch (BObject.BEncodingException e) {
			throw e;
		}

		return extractInt(in);
	}

	private boolean isZero(InputStream in) throws IOException {
		in.mark(2);
		int ch = in.read();
		if (ch == '0') {
			ch = in.read();
			if (ch == 'e') {
				return true;
			} else {
				throw new BObject.BEncodingException("expected 'e' after '0', but got " + (char) ch);
			}
		}
		in.reset();
		return false;
	}

	private BObject extractInt(InputStream in) throws IOException {
		// Check for sign.
		int ch = in.read();
		if (ch == -1) {
			return null;
		}

		StringBuilder buff = new StringBuilder();
		if ((char) ch == '-') {
			if (checkNegativeZero(in)) {
				throw new BObject.BEncodingException("got negative zero");
			} else {
				buff.append((char) ch);
				ch = in.read();
			}
		}

		// Extract the number itself.
		while (ch >= '0' && ch <= '9') {
			buff.append((char) ch);
			if (buff.length() == MAX_SIZE)
				break;
			ch = in.read();
		}

		// Check if the ending boundary has been reached after the number
		// has been extracted.
		if (ch != 'e') {
			throw new BObject.BEncodingException("expected 'e' after number, got " + (char) ch);
		}

		return new BObject(new BigInteger(buff.toString()));
	}

	private boolean checkNegativeZero(InputStream in) throws IOException {
		in.mark(1);
		int ch = in.read();
		if ((char) ch == '0') {
			return true;
		}
		in.reset();

		return false;
	}

	public void encode(Object o, OutputStream out) throws IOException {
		if (!(o instanceof Number)) {
			throw new BObject.BEncodingException(
					"encode: BIntegerCoder expected number, got " + o.getClass().getName());
		}
		out.write('i');
		out.write(((Number) o).toString().getBytes(BObject.BYTE_ENCODING));
		out.write('e');
	}

	// FIXME - remove main
	public static void main(String[] args) {
		BIntegerCoder bic = BIntegerCoder.instance();
		try {
			BObject bo = bic.decode(new ByteArrayInputStream(new String("i-12121684783340e").getBytes()));
			System.out.println(bo.asNumber());
			ByteArrayOutputStream baout = new ByteArrayOutputStream();
			bic.encode(bo.asNumber(), baout);
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
