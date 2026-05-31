package com.aegiskv.storage;

import com.aegiskv.test.Assert;
import java.nio.ByteBuffer;

public class OffHeapMemoryManagerTest {
    
    public void testAllocateAndRead() {
        OffHeapMemoryManager manager = new OffHeapMemoryManager(10); // 10MB slab
        
        String testData = "Hello, Off-Heap World!";
        byte[] bytes = testData.getBytes();
        ByteBuffer writeBuffer = ByteBuffer.wrap(bytes);
        
        int offset = manager.allocateAndWrite(writeBuffer);
        Assert.assertTrue(offset >= 0);
        
        ByteBuffer readBuffer = manager.readDirect(offset, bytes.length);
        byte[] readBytes = new byte[bytes.length];
        readBuffer.get(readBytes);
        
        Assert.assertEquals(testData, new String(readBytes));
    }
    
    public void testOutOfMemory() {
        // Init a very small slab: 1MB = 1048576 bytes
        OffHeapMemoryManager manager = new OffHeapMemoryManager(1); 
        
        // Consume almost all of it
        int offset1 = manager.allocateAndWrite(ByteBuffer.allocateDirect(1000000));
        Assert.assertTrue(offset1 >= 0);
        
        // Attempt to allocate beyond remaining capacity
        int offset2 = manager.allocateAndWrite(ByteBuffer.allocateDirect(100000));
        Assert.assertEquals(-1L, (long) offset2); // Should return -1 for OOM
    }
}
