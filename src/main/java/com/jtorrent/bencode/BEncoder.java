package com.jtorrent.bencode;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

public class BEncoder implements IEncode {
	private static BEncoder _instance;
	
	private BEncoder() {}
	
	public static BEncoder instance() {
		if(_instance == null) {
			_instance = new BEncoder();
		}
		
		return _instance;
	}

	public void encode(Object o, OutputStream out) throws IOException {
		if (o instanceof BObject) {
			o = ((BObject)o).asObject();
		} 
		
		IEncode encoder = null;
		if (o instanceof String) {
			encoder = BByteStringCoder.instance();
		} else if (o instanceof byte[]) {
			encoder = BByteStringCoder.instance();
			o = new String((byte[])o);
		} else if (o instanceof Number) {
			encoder = BIntegerCoder.instance();
		} else if (o instanceof List) {
			encoder = BListCoder.instance();
		} else if (o instanceof Map) {
			encoder = BMapCoder.instance();
		} else {
			throw new IllegalArgumentException("unsupported type " + o.getClass());
		}
		
		encoder.encode(o, out);
	}

}
