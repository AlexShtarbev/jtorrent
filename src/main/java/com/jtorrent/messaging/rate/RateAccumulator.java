package com.jtorrent.messaging.rate;

public class RateAccumulator implements Comparable<RateAccumulator>{

	private long _data;
	private long _startTime;
	private long _endTime;
	
	public long getData() {
		return _data;
	}
	
	public synchronized void accumulate(long bytes) {
		_data += bytes;
		_endTime = System.currentTimeMillis();
		if(_startTime == 0) {
			_startTime = _endTime;
		}
	}
	
	/**
	 * 
	 * @return The rate in bytes/sec
	 */
	public synchronized double rate() {
		if(_startTime == _endTime) {
			return 0;
		}
		
		double elapsedSeconds = ((double)(_endTime - _startTime))/1000.0f;
		
		return _data / elapsedSeconds;
	}
	
	public synchronized void reset() {
		_data = 0;
		_startTime = _endTime = System.currentTimeMillis();
	}

	@Override
	public int compareTo(RateAccumulator o) {
		return rate() > o.rate() ? 1 : -1;
	}
}
