package com.aegiskv.wal;

import com.aegiskv.network.Command;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.logging.Logger;

public final class WriteAheadLog {
    private static final Logger LOGGER = Logger.getLogger(WriteAheadLog.class.getName());
    
    private final FileChannel fileChannel;
    
    // ThreadLocal buffer to prevent allocation during serialization on hot paths
    private static final ThreadLocal<ByteBuffer> THREAD_LOCAL_BUFFER = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(1024 * 1024)); // 1MB Max Entry per write

    public WriteAheadLog(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        // Open for append (read/write)
        RandomAccessFile raf = new RandomAccessFile(file, "rw");
        this.fileChannel = raf.getChannel();
        // Seek to the end for appending new entries
        this.fileChannel.position(this.fileChannel.size());
        LOGGER.info("WAL initialized at " + filePath + ", current size: " + this.fileChannel.size() + " bytes");
    }

    public synchronized void appendPut(ByteBuffer key, ByteBuffer value) throws IOException {
        int keyLen = key.remaining();
        int valLen = value.remaining();
        
        ByteBuffer buffer = THREAD_LOCAL_BUFFER.get();
        buffer.clear();
        
        buffer.put(Command.PUT.getCode());
        buffer.putInt(keyLen);
        buffer.putInt(valLen);
        
        int keyPos = key.position();
        buffer.put(key);
        key.position(keyPos); // Restore position
        
        int valPos = value.position();
        buffer.put(value);
        value.position(valPos); // Restore position
        
        buffer.flip();
        
        while (buffer.hasRemaining()) {
            fileChannel.write(buffer);
        }
        
        // Force sync to disk physically ensuring durability (fsync)
        fileChannel.force(false);
    }
    
    public synchronized void appendDelete(ByteBuffer key) throws IOException {
        int keyLen = key.remaining();
        
        ByteBuffer buffer = THREAD_LOCAL_BUFFER.get();
        buffer.clear();
        
        buffer.put(Command.DELETE.getCode());
        buffer.putInt(keyLen);
        buffer.putInt(0); // 0 length for value
        
        int keyPos = key.position();
        buffer.put(key);
        key.position(keyPos);
        
        buffer.flip();
        
        while (buffer.hasRemaining()) {
            fileChannel.write(buffer);
        }
        
        // Force sync to disk physically
        fileChannel.force(false);
    }
    
    public FileChannel getChannel() {
        return fileChannel;
    }
    
    public void close() throws IOException {
        fileChannel.close();
    }
}
