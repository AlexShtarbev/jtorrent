package com.jtorrent.torrent.restore;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jtorrent.messaging.announce.ConnectionService;
import com.jtorrent.peer.Peer;
import com.jtorrent.torrent.TorrentSession;
import com.jtorrent.torrent.TorrentSession.Status;

public class RestoreManager {
	
	// The file from which the client will restore its previous
	// state and will also update when the client's torrents 
	// are being added/deleted.
	private final String _restoreFile;
		
	public RestoreManager(String filePath) throws JsonGenerationException, JsonMappingException,
			IOException {
		_restoreFile = filePath;
		// Make sure the restore file has an initial form into which it can be appended
		// or deleted when torrents are added/removed.
		if(isRestoreFileEmpty()) {
			createEmptyRestorePoint();
		}
	}
	
	private boolean isRestoreFileEmpty() throws IOException {
		BufferedReader br = new BufferedReader(new FileReader(_restoreFile));     
		try {
			return br.readLine() == null;
		} catch (IOException e) {
			throw e;
		} finally {
			br.close();
		}
	}
	
	private void createEmptyRestorePoint() throws JsonGenerationException, JsonMappingException,
			IOException {
		TorrentClientRestorePoint resotrePoint = new TorrentClientRestorePoint();
		resotrePoint.setTorrentSessions(new LinkedList<>());
		
		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.writeValue(new File(_restoreFile), resotrePoint);
	}
	
	public TorrentClientRestorePoint getLastRestorePoint() throws IOException {
		
		// Read the JSON file data.
		byte[] jsonData = Files.readAllBytes(Paths.get(_restoreFile));
		
		ObjectMapper objectMapper = new ObjectMapper();
		
		TorrentClientRestorePoint resotrePoint;
		resotrePoint = objectMapper.readValue(jsonData, TorrentClientRestorePoint.class);
		
		return resotrePoint;
	}
	
	private TorrentSessionRestorePoint provideRestorePoint(TorrentSession session) {
		TorrentSessionRestorePoint restorePoint = new TorrentSessionRestorePoint();
		restorePoint.setTorrentFile(session.getTorrentFileName());
		restorePoint.setDestinationFolder(session.getDestionationFolder());
		restorePoint.setStopped(session.isStopped());
		
		return restorePoint;
	}
	
	private void updateResotreFile(TorrentClientRestorePoint rp) throws JsonGenerationException, JsonMappingException, IOException {
		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.writeValue(new File(_restoreFile), rp);
	}
	
	public void appendTorrentSession(TorrentSession session) throws JsonGenerationException, JsonMappingException, IOException {
		if (isInRestorePoint(session)) {
			return;
		}
		// Create the new restore point for the torrent session.
		TorrentSessionRestorePoint restorePoint = provideRestorePoint(session);
		
		// Add it to the current list of restore points.
		TorrentClientRestorePoint lastRestore = getLastRestorePoint();
		List<TorrentSessionRestorePoint> restorePoints = lastRestore.getTorrentSessions();
		restorePoints.add(restorePoint);
		
		// Overwrite the existing restore point with the new one.
		updateResotreFile(lastRestore);
	}
	
	public void setTorrentSessionStopped(TorrentSession session, boolean stopped) throws IOException {
		TorrentClientRestorePoint lastRestore = getLastRestorePoint();
		List<TorrentSessionRestorePoint> restorePoints = lastRestore.getTorrentSessions();
		for(TorrentSessionRestorePoint restore : restorePoints) {
			if(restore.getTorrentFile().equals(session.getTorrentFileName())) {
				restore.setStopped(stopped);
				break;
			}
		}
		
		// Overwrite the existing restore point with the new one.
		updateResotreFile(lastRestore);
	}
	
	public boolean isInRestorePoint(TorrentSession session) throws IOException {
		TorrentClientRestorePoint lastRestore = getLastRestorePoint();
		for(TorrentSessionRestorePoint torrentPoint : lastRestore.getTorrentSessions()) {
			if(torrentPoint.getTorrentFile().equals(session.getTorrentFileName())) {
				return true;
			}
		}
		
		return false;
	}
	
	public void removeTorrentSessionRestorePoint(TorrentSession session) throws JsonGenerationException, JsonMappingException, IOException {		
		TorrentClientRestorePoint lastRestore = getLastRestorePoint();
		List<TorrentSessionRestorePoint> restorePoints = lastRestore.getTorrentSessions();
		
		// Search for the restore point of the torrent session and remove it.
		for(int i = 0; i < restorePoints.size(); i++) {
			if(restorePoints.get(i).getTorrentFile().equals(session.getTorrentFileName())) {
				restorePoints.remove(i);
				break;
			}
		}
		
		updateResotreFile(lastRestore);
	}
	
	public List<TorrentSession> restroreTorrentSessions(ConnectionService connService, Peer clientPeer)
			throws IOException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, URISyntaxException {
		
		TorrentClientRestorePoint clientRestore = getLastRestorePoint();
		List<TorrentSession> sessions = new ArrayList<>();
		
		for(TorrentSessionRestorePoint restorePoint : clientRestore.getTorrentSessions()) {
			Status status = restorePoint.getStopped() ? Status.STOPPED : TorrentSession.INITIAL_STATUS;
			TorrentSession session = new TorrentSession(
					restorePoint.getTorrentFile(),
					restorePoint.getDestinationFolder(),
					clientPeer,
					connService,
					status);
			
			sessions.add(session);
		}
		
		return sessions;
	}

	// FIXME
	public static void main(String[] args) {
				
		try {
			String restFile = "torrents.json";
			RestoreManager rm = new RestoreManager(restFile);
			TorrentSession ts1 = new TorrentSession("D:/Movie/orig.torrent", "D:/Movie/dir", null, null, Status.STOPPED);
			TorrentSession ts2 = new TorrentSession("D:/Movie/vamp.torrent", "D:/Movie/dir", null, null);
			
			rm.appendTorrentSession(ts1);			
			rm.appendTorrentSession(ts2);
			TorrentClientRestorePoint rp = rm.getLastRestorePoint();
			System.out.print(rp.toString());			
			
			System.out.println("---------------------");
			ts1.setStatus(Status.DOWNLOADING);
			rm.setTorrentSessionStopped(ts1, ts1.isStopped());
			rp = rm.getLastRestorePoint();
			System.out.print(rp.toString());
			
			System.out.println("---------------------");
			ts2.setStatus(Status.STOPPED);
			rm.setTorrentSessionStopped(ts2, ts2.isStopped());
			rp = rm.getLastRestorePoint();
			System.out.print(rp.toString());
			
			/*rm.removeTorrentSessionRestorePoint(ts1);
			rp = rm.getLastRestorePoint();
			System.out.print(rp.toString());
			
			rm.appendTorrentSession(ts2);
			rm.appendTorrentSession(ts2);
			rm.appendTorrentSession(ts2);
			rp = rm.getLastRestorePoint();
			System.out.print(rp.toString());*/
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
	}
		
}
