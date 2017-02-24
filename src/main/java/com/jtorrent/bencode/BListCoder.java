package com.jtorrent.bencode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

public class BListCoder implements IDecode, IEncode {
	
	private static BListCoder _instance;
	
	private BListCoder() {}
	
	public static BListCoder instance() {
		if (_instance == null) {
			_instance = new BListCoder();
		}
		
		return _instance;
	}

	public BObject decode(InputStream in) throws IOException {
		int ch = in.read();
		if(ch == -1) {
			return null;
		}
		
		if((char)ch != 'l') {
			throw new BObject.BEncodingException("expected 'l', got " + (char)ch);
		}
		
		List<BObject> list = new ArrayList<BObject>();
		
		while(!BDecoder.instance().isEndSentinel(in)) {
			list.add(BDecoder.instance().decode(in));
		}
		
		return new BObject(list);
	}

	@SuppressWarnings("unchecked")
	public void encode(Object o, OutputStream out) throws IOException {
		if(!(o instanceof List)) {
			throw new BObject.BEncodingException("expected List<BObject>, got " + o.getClass().getCanonicalName());
		}
		out.write('l');
		// Lists may contain any bencoded type, including integers, strings, dictionaries, and even lists within other lists.
		// The list is iterated trough and each distinct object is encoded by itself.
		for (BObject value : (List<BObject>)o) {
			BEncoder.instance().encode(value, out);
		}
		out.write('e');
	}
	
	// FIXME
	public static void main(String[] args) {
		BListCoder blc = BListCoder.instance();
		try {
			BObject res = blc.decode(new ByteArrayInputStream(new String("l4:spam4:eggsi-554el2:hii45eee").getBytes()));
			/*for(BObject obj : res.asList()){
				System.out.println(obj.asString());
			}*/
			ByteArrayOutputStream baout = new ByteArrayOutputStream();
			blc.encode(res.asList(), baout);
			System.out.println(baout.toString());
			baout.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
