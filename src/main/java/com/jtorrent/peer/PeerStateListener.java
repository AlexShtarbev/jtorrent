package com.jtorrent.peer;

import java.util.EventListener;

public interface PeerStateListener extends EventListener{

	public void onPeerDisconnected(Peer peer);
}
