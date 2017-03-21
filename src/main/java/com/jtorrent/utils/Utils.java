package com.jtorrent.utils;

import java.nio.ByteBuffer;
import java.util.BitSet;

public class Utils {

	public static BitSet convertByteBufferToBitSet(ByteBuffer payload) {
		// Every byte consists of 8 bits. The total size of the bit set
		// is [number of bytes] * [number of bits in byte].
		int bitFieldSize = payload.remaining() * Byte.SIZE;
		BitSet bitSet = new BitSet(bitFieldSize);
		for (int i = 0; i < bitFieldSize; i++) {
			// Calculate in which byte the bit is.
			int byteIndex = i / Byte.SIZE;
			// Find the position of the byte. The leftmost byte has index 0.
			// The rightmost byte has index 7 (Byte.SIZE - 1).
			// The position is found by calculating how many bits there are
			// until the right most byte.
			int posInByte = Byte.SIZE - i % Byte.SIZE - 1;
			int bitValue = payload.get(byteIndex) & (1 << posInByte);
			if (bitValue > 0) {
				bitSet.set(i);
			}
		}

		return bitSet;
	}

	public static ByteBuffer convertBitSetToByteBuffer(BitSet bitSet, int numPieces) {
		int bitfieldSize = (int) Math.ceil((double) numPieces / Byte.SIZE);
		byte[] bitField = new byte[bitfieldSize];

		for (int i = bitSet.nextSetBit(0); 0 <= i && i < numPieces; i = bitSet.nextSetBit(i + 1)) {
			// Calculate in which byte the bit is.
			int byteIndex = i / Byte.SIZE;
			// Find the position of the byte. The leftmost byte has index 0.
			// The rightmost byte has index 7 (Byte.SIZE - 1).
			// The position is found by calculating how many bits there are
			// until the right most byte.
			int posInByte = Byte.SIZE - i % Byte.SIZE - 1;
			bitField[byteIndex] |= 1 << posInByte;
		}

		return ByteBuffer.wrap(bitField);
	}
}
