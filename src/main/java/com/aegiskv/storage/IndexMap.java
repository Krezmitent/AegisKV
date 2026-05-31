package com.aegiskv.storage;

import com.aegiskv.util.ByteArrayKey;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;

public final class IndexMap {
    // Value: Encoded Long (Upper 32 bits = off-heap offset, Lower 32 bits = length)
    private final ConcurrentHashMap<ByteArrayKey, Long> map;

    public IndexMap() {
        // Pre-allocate to prevent resizing overhead
        this.map = new ConcurrentHashMap<>(100_000);
    }

    public void put(ByteBuffer keyBuffer, int offset, int length) {
        long value = ((long) offset << 32) | (length & 0xFFFFFFFFL);
        // Allocation happens once during PUT
        map.put(new ByteArrayKey(keyBuffer), value);
    }

    public Long get(ByteBuffer keyBuffer) {
        // Zero-GC lookup using ThreadLocal wrapper
        ByteArrayKey lookupKey = ByteArrayKey.forLookup(keyBuffer);
        return map.get(lookupKey);
    }

    public Long remove(ByteBuffer keyBuffer) {
        // Zero-GC lookup using ThreadLocal wrapper
        ByteArrayKey lookupKey = ByteArrayKey.forLookup(keyBuffer);
        return map.remove(lookupKey);
    }
    
    public static int extractOffset(long value) {
        return (int) (value >> 32);
    }
    
    public static int extractLength(long value) {
        return (int) value;
    }
}
