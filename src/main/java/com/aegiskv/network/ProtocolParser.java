package com.aegiskv.network;

import com.aegiskv.util.Config;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public final class ProtocolParser {
    private static final int HEADER_SIZE = 10;
    
    private int expectedKeyLength = -1;
    private int expectedValueLength = -1;
    private Command currentCommand = Command.UNKNOWN;
    
    public Request parse(SocketChannel channel, ByteBuffer readBuffer) {
        if (expectedKeyLength == -1) {
            // Trying to read header
            if (readBuffer.remaining() < HEADER_SIZE) {
                return null; // Wait for more data
            }
            
            byte magic = readBuffer.get();
            if (magic != Config.MAGIC_NUMBER) {
                throw new IllegalStateException("Invalid magic number: " + magic);
            }
            
            byte cmdByte = readBuffer.get();
            currentCommand = Command.fromCode(cmdByte);
            expectedKeyLength = readBuffer.getInt();
            expectedValueLength = readBuffer.getInt();
            
            if (expectedKeyLength < 0 || expectedValueLength < 0) {
                throw new IllegalStateException("Invalid payload lengths: Key=" + expectedKeyLength + ", Value=" + expectedValueLength);
            }
        }
        
        int totalPayloadSize = expectedKeyLength + expectedValueLength;
        if (readBuffer.remaining() < totalPayloadSize) {
            return null; // Wait for more data
        }
        
        // We have a full frame. Slice buffers to avoid allocating byte arrays.
        ByteBuffer keySlice = null;
        if (expectedKeyLength > 0) {
            keySlice = readBuffer.slice();
            keySlice.limit(expectedKeyLength);
            readBuffer.position(readBuffer.position() + expectedKeyLength);
        }
        
        ByteBuffer valueSlice = null;
        if (expectedValueLength > 0) {
            valueSlice = readBuffer.slice();
            valueSlice.limit(expectedValueLength);
            readBuffer.position(readBuffer.position() + expectedValueLength);
        }
        
        Request request = new Request(channel, currentCommand, keySlice, valueSlice);
        
        // Reset state for next frame
        reset();
        
        return request;
    }
    
    private void reset() {
        expectedKeyLength = -1;
        expectedValueLength = -1;
        currentCommand = Command.UNKNOWN;
    }
}
