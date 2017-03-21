package com.jtorrent.bencode;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BObject {
	public static final String BYTE_ENCODING = "UTF-8";

	private final Object _bobj;

	public BObject(byte[] bytes) {
		_bobj = bytes;
	}

	public BObject(String str) throws UnsupportedEncodingException {
		_bobj = str.getBytes("UTF-8");
	}

	public BObject(String str, String enc) throws UnsupportedEncodingException {
		_bobj = str.getBytes(enc);
	}

	public BObject(int intNum) {
		_bobj = new Integer(intNum);
	}

	public BObject(long longNum) {
		_bobj = new Long(longNum);
	}

	public BObject(Number num) {
		_bobj = num;
	}

	public BObject(List<BObject> list) {
		_bobj = list;
	}

	public BObject(Map<String, BObject> map) {
		_bobj = map;
	}

	public Object asObject() {
		return _bobj;
	}

	public String asString() throws BEncodingException {
		return asString("UTF-8");
	}

	public String asString(String encoding) throws BEncodingException {
		try {
			return new String(asBytes(), encoding);
		} catch (ClassCastException cce) {
			throw new BEncodingException(cce.toString());
		} catch (UnsupportedEncodingException uee) {
			throw new InternalError(uee.toString());
		}
	}

	public byte[] asBytes() throws BEncodingException {
		try {
			if (_bobj instanceof BigInteger) {
				return ((BigInteger) _bobj).toString().getBytes();
			} else {
				return (byte[]) _bobj;
			}
		} catch (ClassCastException cce) {
			throw new BEncodingException(cce.toString());
		}
	}

	public Number asNumber() throws BEncodingException {
		try {
			return (Number) _bobj;
		} catch (ClassCastException cce) {
			throw new BEncodingException(cce.toString());
		}
	}

	public short asShort() throws BEncodingException {
		return asNumber().shortValue();
	}

	public int asInt() throws BEncodingException {
		return asNumber().intValue();
	}

	public long asLong() throws BEncodingException {
		return asNumber().longValue();
	}

	@SuppressWarnings("unchecked")
	public List<BObject> asList() throws BEncodingException {
		if (_bobj instanceof ArrayList) {
			return (ArrayList<BObject>) _bobj;
		} else {
			throw new BEncodingException("Excepted List<BObject>, got " + _bobj.getClass().getCanonicalName());
		}
	}

	@SuppressWarnings("unchecked")
	public Map<String, BObject> asMap() throws BEncodingException {
		if (_bobj instanceof HashMap) {
			return (Map<String, BObject>) _bobj;
		} else {
			throw new BEncodingException("Expected Map<String, BObject>, got " + _bobj.getClass().getCanonicalName());
		}
	}

	public static class BEncodingException extends IOException {

		public static final long serialVersionUID = -1;

		public BEncodingException(String message) {
			super(message);
		}
	}
}
