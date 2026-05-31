package com.aegiskv.util;

import java.nio.ByteBuffer;
import java.util.Arrays;

public final class ByteArrayKey {
    private byte[] data;
    private ByteBuffer buffer; // Used only for zero-allocation lookups
    private int hash;
    private boolean isLookupOnly;

    // For permanent storage in the map (PUT)
    public ByteArrayKey(ByteBuffer source) {
        int length = source.remaining();
        this.data = new byte[length];
        int pos = source.position();
        source.get(this.data);
        source.position(pos); // Reset position for downstream use
        this.hash = Arrays.hashCode(this.data);
        this.isLookupOnly = false;
    }

    // For zero-allocation lookups (GET/DELETE)
    private ByteArrayKey() {
        this.isLookupOnly = true;
    }
    
    // ThreadLocal wrapper to avoid creating objects during read path
    private static final ThreadLocal<ByteArrayKey> LOOKUP_KEY = ThreadLocal.withInitial(ByteArrayKey::new);
    
    public static ByteArrayKey forLookup(ByteBuffer buffer) {
        ByteArrayKey key = LOOKUP_KEY.get();
        key.buffer = buffer;
        
        // Calculate hash directly from ByteBuffer without allocating a byte array
        int h = 1;
        int pos = buffer.position();
        int limit = buffer.limit();
        for (int i = pos; i < limit; i++) {
            h = 31 * h + buffer.get(i);
        }
        key.hash = h;
        return key;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj instanceof ByteArrayKey) {
            ByteArrayKey that = (ByteArrayKey) obj;
            return compare(this, that);
        }
        return false;
    }
    
    private static boolean compare(ByteArrayKey a, ByteArrayKey b) {
        if (a.isLookupOnly && !b.isLookupOnly) {
            return compareBufferAndArray(a.buffer, b.data);
        } else if (!a.isLookupOnly && b.isLookupOnly) {
            return compareBufferAndArray(b.buffer, a.data);
        } else if (!a.isLookupOnly && !b.isLookupOnly) {
            return Arrays.equals(a.data, b.data);
        } else {
            return a.buffer.equals(b.buffer);
        }
    }
    
    private static boolean compareBufferAndArray(ByteBuffer buf, byte[] arr) {
        if (buf.remaining() != arr.length) return false;
        int pos = buf.position();
        for (int i = 0; i < arr.length; i++) {
            if (buf.get(pos + i) != arr[i]) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
