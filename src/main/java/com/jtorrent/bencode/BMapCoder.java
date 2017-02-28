package com.jtorrent.bencode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BMapCoder implements IDecode, IEncode {
	private static BMapCoder _instance;
	
	private BMapCoder() {}
	
	public static BMapCoder instance() {
		if (_instance == null) {
			_instance = new BMapCoder();
		}
		
		return _instance;
	}

	@Override
	public BObject decode(InputStream in) throws IOException {
		int ch = in.read();
		if(ch == -1) {
			return null;
		}
		
		if((char)ch != 'd') {
			throw new BObject.BEncodingException("expected 'd', got " + (char)ch);
		}
		
		// The documentation states that  the keys must be bencoded strings.
		// https://wiki.theory.org/BitTorrentSpecification.
		Map<String, BObject> map = new HashMap<String, BObject>();
		
		BDecoder bdecoder = BDecoder.instance();
		while(!bdecoder.isEndSentinel(in)) {
			String key = bdecoder.decode(in).asString();
			BObject value = bdecoder.decode(in);
			map.put(key, value);
		}
		
		return new BObject(map);
	}
	

	@SuppressWarnings("unchecked")
	@Override
	public void encode(Object o, OutputStream out) throws IOException {
		if(!(o instanceof Map)) {
			throw new BObject.BEncodingException("expected Map<BObject>, got " + o.getClass().getCanonicalName());
		}
		
		Set<String> keySet = ((Map<String, BObject>)o).keySet();
		List<String> list = new ArrayList<String>(keySet);
		Collections.sort(list);

		out.write('d');
		for (String key : list) {
			Object value = ((Map<String, BObject>)o).get(key);
			BEncoder.instance().encode(key, out);
			BEncoder.instance().encode(value, out);
		}
		out.write('e');
	}
	
	// FIXME
	public static void main(String[] args) {
		BMapCoder bmc = BMapCoder.instance();
		try {
			BObject res = bmc.decode(new ByteArrayInputStream(new String("d4:listli5ei66ee4:spam4:eggs2:hii5ee").getBytes()));
			ByteArrayOutputStream baout = new ByteArrayOutputStream();
			bmc.encode(res.asMap(), baout);
			System.out.println(baout.toString());
			baout.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
