package com.aegiskv.wal;

import com.aegiskv.network.Command;
import com.aegiskv.storage.StorageEngine;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.logging.Logger;

public final class RecoveryManager {
    private static final Logger LOGGER = Logger.getLogger(RecoveryManager.class.getName());
    
    public static void recover(WriteAheadLog wal, StorageEngine storageEngine) throws IOException {
        FileChannel channel = wal.getChannel();
        long originalPosition = channel.position();
        
        if (originalPosition == 0) {
            LOGGER.info("WAL is empty. Starting fresh.");
            return;
        }
        
        LOGGER.info("Starting WAL recovery. Processing " + originalPosition + " bytes...");
        channel.position(0);
        
        // Use a 1MB buffer for reading from disk incrementally
        ByteBuffer readBuffer = ByteBuffer.allocateDirect(1024 * 1024); 
        
        int entriesRecovered = 0;
        
        while (channel.position() < originalPosition) {
            readBuffer.clear();
            channel.read(readBuffer);
            readBuffer.flip();
            
            while (readBuffer.remaining() >= 9) { // 1 byte cmd + 4 byte keyLen + 4 byte valLen
                readBuffer.mark();
                byte cmdByte = readBuffer.get();
                int keyLen = readBuffer.getInt();
                int valLen = readBuffer.getInt();
                
                if (readBuffer.remaining() < keyLen + valLen) {
                    // Incomplete entry at the end of the buffer chunk, rewind and fetch more
                    readBuffer.reset();
                    break;
                }
                
                Command command = Command.fromCode(cmdByte);
                
                ByteBuffer keySlice = readBuffer.slice();
                keySlice.limit(keyLen);
                readBuffer.position(readBuffer.position() + keyLen);
                
                ByteBuffer valSlice = readBuffer.slice();
                valSlice.limit(valLen);
                readBuffer.position(readBuffer.position() + valLen);
                
                // Replay the command natively via the storage engine (bypassing WAL appending)
                if (command == Command.PUT) {
                    storageEngine.recoverPut(keySlice, valSlice);
                } else if (command == Command.DELETE) {
                    storageEngine.recoverDelete(keySlice);
                }
                
                entriesRecovered++;
            }
            
            // Adjust position for the next read if we had partial data
            if (readBuffer.hasRemaining()) {
                channel.position(channel.position() - readBuffer.remaining());
            }
        }
        
        // Restore position back to the end for future appends
        channel.position(originalPosition);
        LOGGER.info("WAL recovery complete. Replayed " + entriesRecovered + " entries into memory.");
    }
}
