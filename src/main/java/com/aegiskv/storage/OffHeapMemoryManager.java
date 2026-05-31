package com.aegiskv.storage;

import java.nio.ByteBuffer;
import java.util.concurrent.locks.StampedLock;
import java.util.logging.Logger;

public final class OffHeapMemoryManager {
    private static final Logger LOGGER = Logger.getLogger(OffHeapMemoryManager.class.getName());
    
    private final ByteBuffer memorySlab;
    private final int capacity;
    
    // Simple linear allocator state
    private int currentOffset = 0;
    
    // Concurrency control for slab modifications
    private final StampedLock lock = new StampedLock();

    public OffHeapMemoryManager(int capacityMB) {
        this.capacity = capacityMB * 1024 * 1024;
        this.memorySlab = ByteBuffer.allocateDirect(this.capacity);
        LOGGER.info("Initialized Off-Heap memory slab with " + capacityMB + " MB");
    }

    /**
     * Appends value into the memory slab.
     * @return the offset of the allocated space, or -1 if Out of Memory.
     */
    public int allocateAndWrite(ByteBuffer valueBuffer) {
        int length = valueBuffer.remaining();
        long stamp = lock.writeLock();
        try {
            if (currentOffset + length > capacity) {
                // Production KV stores would trigger compaction or use a free-list block manager here.
                LOGGER.warning("Out of Off-Heap memory!");
                return -1; 
            }
            
            int allocatedOffset = currentOffset;
            
            // Create a temporary view to avoid interfering with the main slab's position
            ByteBuffer slice = memorySlab.duplicate();
            slice.position(allocatedOffset);
            slice.put(valueBuffer);
            
            currentOffset += length;
            return allocatedOffset;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    /**
     * Reads a value from the direct memory without copying into standard heap arrays.
     * @return a zero-copy sliced view of the requested byte range.
     */
    public ByteBuffer readDirect(int offset, int length) {
        // Optimistic read
        long stamp = lock.tryOptimisticRead();
        ByteBuffer slice = memorySlab.duplicate();
        slice.position(offset);
        slice.limit(offset + length);
        
        if (!lock.validate(stamp)) {
            // Fallback to strict read lock
            stamp = lock.readLock();
            try {
                slice = memorySlab.duplicate();
                slice.position(offset);
                slice.limit(offset + length);
            } finally {
                lock.unlockRead(stamp);
            }
        }
        
        return slice.slice();
    }
}
