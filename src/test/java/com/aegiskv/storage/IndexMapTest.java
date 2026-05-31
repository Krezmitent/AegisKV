package com.aegiskv.storage;

import com.aegiskv.test.Assert;
import java.nio.ByteBuffer;

public class IndexMapTest {
    
    public void testPutAndGetZeroAllocation() {
        IndexMap map = new IndexMap();
        
        byte[] keyBytes = "myKey".getBytes();
        ByteBuffer keyBuffer = ByteBuffer.wrap(keyBytes);
        
        int offset = 1024;
        int length = 50;
        
        map.put(keyBuffer, offset, length);
        
        // Simulate a lookup from the network using a new ByteBuffer spanning the same bytes
        ByteBuffer lookupBuffer = ByteBuffer.wrap("myKey".getBytes());
        Long value = map.get(lookupBuffer);
        
        Assert.assertNotNull(value);
        Assert.assertEquals((long) offset, (long) IndexMap.extractOffset(value));
        Assert.assertEquals((long) length, (long) IndexMap.extractLength(value));
    }
    
    public void testRemove() {
        IndexMap map = new IndexMap();
        ByteBuffer keyBuffer = ByteBuffer.wrap("delKey".getBytes());
        map.put(keyBuffer, 0, 10);
        
        Long val1 = map.get(keyBuffer);
        Assert.assertNotNull(val1);
        
        map.remove(ByteBuffer.wrap("delKey".getBytes()));
        
        Long val2 = map.get(ByteBuffer.wrap("delKey".getBytes()));
        Assert.assertNull(val2);
    }
}
