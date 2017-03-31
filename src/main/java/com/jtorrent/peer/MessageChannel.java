package com.jtorrent.peer;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jtorrent.messaging.message.KeepAliveMessage;
import com.jtorrent.messaging.message.Message;
import com.jtorrent.messaging.message.MessageExchangeException;
import com.jtorrent.messaging.message.MessageListener;
import com.jtorrent.messaging.message.Messages;
import com.jtorrent.torrent.TorrentSession;

// TODO - add documentation
public class MessageChannel {
	
	private static final Logger _logger = LoggerFactory.getLogger(MessageChannel.class);
	
	private final SocketChannel _socketChannel;
	
	private BlockingQueue<ByteBuffer> _messageQueue;
	
	private List<MessageListener> _listeners;
	
	private ExecutorService _messageService;
	
	private boolean _closed;
	
	private final TorrentSession _torrentSession;
	private final Peer _peer;
	
	private MessageChannel(SocketChannel socketChannel, TorrentSession torrentSession, Peer peer) throws IOException {
		_socketChannel = socketChannel;
		
		_messageQueue = new LinkedBlockingQueue<ByteBuffer>();
		
		_listeners = new LinkedList<MessageListener>();
		
		_torrentSession = torrentSession;
		_peer = peer;
		
		MessageReceiveTask recv = new MessageReceiveTask();
		MessageSendTask send = new MessageSendTask();		
		_messageService = Executors.newCachedThreadPool();
		_messageService.execute(send);
		_messageService.execute(recv);		
	}
	
	public static MessageChannel open(SocketChannel socketChannel, TorrentSession torrentSession, Peer peer) throws IOException {
		return new MessageChannel(socketChannel, torrentSession, peer);
	}

	public void close() {
		_closed = true;		
		_messageService.shutdown();
		if(_socketChannel.isConnected()) {
			IOUtils.closeQuietly(_socketChannel);
		}
	}
	
	public void send(ByteBuffer msg) {
		try {
			_messageQueue.put(msg);
		} catch (InterruptedException e) {
			_logger.warn("MESSAGE QUEUE BUSTED!!");
		}
	}
	
	public boolean isConnected() {
		return _socketChannel.isConnected();
	}
	
	public void addMessageListener(MessageListener listener) {
		_listeners.add(listener);
	}
	
	private void notifyExceptionListeners(Exception e) {
		for(MessageListener l : _listeners) {
			l.onMessageChannelException(e);
		}
	}
	
	private void notifyMessageListeners(ByteBuffer msg) {
		for(MessageListener l : _listeners) {
			l.onMessageReceived(msg);
		}
	}
	
	/**
	 * <p>MessageSendTask has the job of sending the messages that it receives on the 
	 * message queue. This process is done by immediately forwarding the messages in 
	 * the order they arrived at the message channel.</p>
	 * 
	 * <p>Should no message arrive during a 2 minute period, a keep-alive message is sent
	 * so that the connection with the peer is kept open</p>
	 * @author Alex
	 *
	 */
	private class MessageSendTask implements Runnable {

		private static final int KEEP_ALIVE_TIMEOUT = 2;
		
		@Override
		public void run() {
			while(!_closed || (_closed && _messageQueue.size() > 0)) {
				try {
					ByteBuffer msg = _messageQueue.poll(KEEP_ALIVE_TIMEOUT, TimeUnit.MINUTES);
					if (msg == null) {
						msg = KeepAliveMessage.make();
					}
										
					String message = null;
					try {
						ByteBuffer dup = msg.duplicate();
						dup.rewind();
						message = Messages.parse(_torrentSession, dup).getMessageType().toString();
						_logger.debug("Peer {} is trying to send {} bytes {} message", _peer.getHostAddress(), msg.capacity(), message);
					} catch (MessageExchangeException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					msg.rewind();
					// Send the message on the socket channel.
					while(!_closed && msg.hasRemaining()) {
						int sent = _socketChannel.write(msg);
						if(sent < 0) {
							_logger.debug("DAMN IT!!! could not send message {}", message);
							EOFException e = new EOFException("unexpected end of stream while sending " + message);
							notifyExceptionListeners(e);
							return;
						} else {
							_logger.debug("Sent {} bytes {} to peer {}", sent, message, _peer.getHostAddress());
						}
						
					}
				} catch (InterruptedException | IOException e) {
					notifyExceptionListeners(e);
				}
			}
		}
		
	}
	
	private class MessageReceiveTask implements Runnable {

		/**
		 * Used for determining the state of the socket channel.
		 */
		private Selector _selector;
		
		@Override
		public void run() {
			// Allocate 1 MB for the buffer.
			ByteBuffer message = ByteBuffer.allocateDirect(1*1024*1024);
			try {
				_selector = Selector.open();
				_socketChannel.register(_selector, SelectionKey.OP_READ);
				
				while(!_closed) {
					_logger.info("Trying to read from peer {}...", _peer.getHostAddress());
					message.rewind();
					// Read the length of the message first.
					message.limit(Message.LENGTH_FIELD_SIZE);
					while(!_closed && message.hasRemaining()) {
						read(message);
					}
										
					// Get ready to accept the entire message.
					int length = message.getInt(0);
					_logger.debug("Trying to read message with <len={}> from peer {}", length, _peer.getHostAddress());

					message.limit(Message.LENGTH_FIELD_SIZE + length);
					while(!_closed && message.hasRemaining()) {
						read(message);
					}
															
					// Now rewind the buffer and send the newly arrived message to
					// all interested listeners.
					message.rewind();
					
					if(_closed) {
						break;
					}
					
					notifyMessageListeners(message);
				}
			} catch (IOException e) {
				notifyExceptionListeners(e);
			} finally{
				if(_selector != null) {
					try {
						_selector.close();
					} catch (IOException e) {
						notifyExceptionListeners(e);
					}
				}
			}
			
			_logger.info("Read channel for peer {} closed...", _peer.getHostAddress());
			
		}
		
		private long read(ByteBuffer message) throws IOException {
			if(_selector.select() == 0 || !message.hasRemaining()) {
				return 0;
			}
			
			long read = 0;
			Iterator<SelectionKey> it = _selector.selectedKeys().iterator();
			// Traverse all the keys.
			// In fact we only have one channel registered with this selector,
			// but the code below is written like that for consistency.

			while(it.hasNext()) {
				SelectionKey selectionKey = (SelectionKey) it.next();
				if(selectionKey.isValid() & selectionKey.isReadable()) {
					int bytes = _socketChannel.read(message);
					if (bytes < 0) {
						throw new IOException("unexpected end of stream while reading with " + read + " read so far / reamining = " + message.remaining());
					}
					read += bytes;
				}				
				it.remove();
			}
			return read;			
		}
	}
}
