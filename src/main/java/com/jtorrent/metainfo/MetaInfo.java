package com.jtorrent.metainfo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.InvalidAlgorithmParameterException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import org.apache.commons.io.FileUtils;

import com.jtorrent.bencode.*;
import com.jtorrent.bencode.BObject.BEncodingException;

/**
 * Parses and contains the meta info file structure. 
 * <br/><br/>
 * This class follows the specification described in 
 * <a href="https://wiki.theory.org/BitTorrentSpecification#Metainfo_File_Structure">Metainfo File Structure</a>.
 * @author Alex
 *
 */
public class MetaInfo {
	// Keys in the Metainfo file structure.
	public static final String INFO_KEY = "info";
	public static final String ANNOUCE_KEY = "announce";
	public static final String ANNOUCE_LIST_KEY = "announce-list";
	public static final String CREATION_DATE_KEY = "creation date";
	public static final String COMMENT_KEY = "comment-list";
	public static final String CREATED_BY_KEY = "created by";
	public static final String ENCODING_KEY = "encoding";
	
	public static final String HASHING_ALGORITHM = "SHA-1";
	
	// Fileds.
	private final Map<String, BObject> _decodedMetaInfo;
	private final Map<String, BObject> _info;
	private final List<List<URI>> _announceList;
	private final Date _creationDate;
	private final String _comment;
	private final String _createdBy;
	private final String _encoding;
	
	private final InfoDictionary _infoDictionary;
	
	/**
	 * Urlencoded 20-byte SHA1 hash of the value of the info key from the Metainfo file.
	 * Note that the value will be a bencoded dictionary.
	 */
	private final byte[] _infoHash;
	
	
	public MetaInfo(File torrentFile) throws IOException, NoSuchAlgorithmException, URISyntaxException, InvalidAlgorithmParameterException {
		byte[] metaInfo = FileUtils.readFileToByteArray(torrentFile);
		InputStream inputStream = new ByteArrayInputStream(metaInfo);

		// Decode the meta info and extract the required components.
		_decodedMetaInfo = BDecoder.instance().decode(inputStream).asMap();
		
		if(!_decodedMetaInfo.containsKey(INFO_KEY)) {
			throw new InvalidAlgorithmParameterException("the meta info file does not contain 'info'key");
		}
		_info = _decodedMetaInfo.get(INFO_KEY).asMap();
		_infoHash = provideInfoHash();
		_announceList = provideAnnounceList();
		_creationDate = provideCreationDate();
		_createdBy = provideCreatedBy();
		_comment = provideComment();
		_encoding = provideEncoding();
		_infoDictionary = new InfoDictionary(_info);
	}
	
	private byte[] provideInfoHash() throws IOException, NoSuchAlgorithmException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		// First the info from the meta file needs to be encoded.
		BEncoder.instance().encode(_info, out);
		
		// The encoded date is encrypted using the sha1 hashing algorithm.
		MessageDigest encryptedInfo = MessageDigest.getInstance(HASHING_ALGORITHM);
		encryptedInfo.reset();
		encryptedInfo.update(out.toByteArray());
		
		return encryptedInfo.digest();
	}
	
	/**
	 * The announce-list extension is supported in our implementation.
	 * <br/>
	 * For ease of use, if the meta info file provides only an announce key, 
	 * then a single tier list is created containing the tracker URI. Otherwise,
	 * the announce-list is parsed accordingly. 
	 * @see <a href="http://bittorrent.org/beps/bep_0012.html">BitTorrent BEP#0012 "Multitracker Metadata Extension"</a>
	 * @param info
	 * @return
	 * @throws URISyntaxException 
	 * @throws IOException 
	 * @throws BEncodingException 
	 */
	private List<List<URI>> provideAnnounceList() throws BEncodingException, IOException, URISyntaxException {
		List<List<URI>> list = new ArrayList<List<URI>>();
		if(_decodedMetaInfo.containsKey(ANNOUCE_LIST_KEY)) {
			list = provideAnnounceListTiers(_decodedMetaInfo.get(ANNOUCE_LIST_KEY).asList());
		} else if(_decodedMetaInfo.containsKey(ANNOUCE_KEY)) {
			String tracker = _decodedMetaInfo.get(ANNOUCE_KEY).asString();
			URI uri = new URI(tracker);
			
			List<URI> singleURItier = new ArrayList<URI>();
			singleURItier.add(uri);
			list.add(singleURItier);
		}
		
		return list;
	}
	
	private List<List<URI>> provideAnnounceListTiers(List<BObject> tierList) throws IOException, URISyntaxException {
		// The set is used to make sure there are no repeating tracker URIs in the tier list.
		List<List<URI>> list = new ArrayList<List<URI>>();
		Set<URI> uriSet = new HashSet<>();
		for(BObject tier : tierList) {
			List<BObject> trackers = tier.asList();
			if(trackers.isEmpty()) continue;
			
			List<URI> tierURIList = new ArrayList<URI>();
			for(BObject tracker : trackers) {
				URI uri = new URI(tracker.asString());
				if(!uriSet.contains(uri)){
					tierURIList.add(uri);
					uriSet.add(uri);
				}
			}
			
			if(!tierURIList.isEmpty()) {
				list.add(tierURIList);
			}
		}
		
		return list;
	}
	
	private Date provideCreationDate() throws BEncodingException {
		BObject dateObject = _decodedMetaInfo.get(CREATION_DATE_KEY);
		if(dateObject == null) {
			return null;
		}
		
		Long dateUnixFormat = dateObject.asLong();
		return new Date(dateUnixFormat);
	}
	
	private String provideComment() throws BEncodingException {
		BObject commentObject = _decodedMetaInfo.get(COMMENT_KEY);
		if(commentObject == null) {
			return null;
		}
		
		return commentObject.asString();
	}
	
	private String provideCreatedBy() throws BEncodingException {
		BObject createByObject = _decodedMetaInfo.get(CREATED_BY_KEY);
		if(createByObject == null) {
			return null;
		}
		
		return createByObject.asString();
	}
	
	private String provideEncoding() throws BEncodingException {
		BObject encodingObject = _decodedMetaInfo.get(ENCODING_KEY);
		if(encodingObject == null) {
			return null;
		}
		
		return encodingObject.asString();
	}

	public List<List<URI>> getAnnounceList() {
		return _announceList;
	}

	public Date getCreationDate() {
		return _creationDate;
	}

	public String getComment() {
		return _comment;
	}

	public String getCreatedBy() {
		return _createdBy;
	}

	public String getEncoding() {
		return _encoding;
	}

	public byte[] getInfoHash() {
		return _infoHash;
	}
	
	public InfoDictionary getInfoDictionary() {
		return _infoDictionary;
	}

	// FIXME
	public static void main(String[] args) {
		File testFile = new File("D:/Movie/assas.torrent");
		try {
			MetaInfo mi = new MetaInfo(testFile);
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidAlgorithmParameterException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
