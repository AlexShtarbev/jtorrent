package com.jtorrent.metainfo;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.jtorrent.bencode.BObject;
import com.jtorrent.bencode.BObject.BEncodingException;

public class FileDictionary {
	public static final String PATH_KEY = "path";
	public static final String LENGHT_KEY = "length";

	private final File _file;
	private final long _length;

	public FileDictionary(String name, String parentDir, long length) {
		_file = new File(name, parentDir);
		_length = length;
	}

	public FileDictionary(String name, long length) {
		_file = new File(name);
		_length = length;
	}

	public File getFile() {
		return _file;
	}

	public long getLength() {
		return _length;
	}

	public static List<FileDictionary> fromFiles(String dirName, List<BObject> filesList) throws BEncodingException {
		List<FileDictionary> fileDictList = new ArrayList<FileDictionary>();
		for (BObject file : filesList) {
			Map<String, BObject> info = file.asMap();

			StringBuilder sb = new StringBuilder();
			List<BObject> pathElements = info.get(PATH_KEY).asList();
			if (pathElements.isEmpty()) {
				throw new IllegalArgumentException("no path elements provided");
			}

			// Form the full path to the resource.
			for (BObject elem : pathElements) {
				sb.append(File.separator);
				sb.append(elem.asString());
			}

			fileDictList.add(new FileDictionary(dirName, sb.toString(), info.get(LENGHT_KEY).asLong()));
		}
		return fileDictList;
	}
}
