package com.aegiskv.network;

import com.aegiskv.test.Assert;
import com.aegiskv.util.Config;

import java.nio.ByteBuffer;

public class ProtocolParserTest {
    
    public void testCompleteFrameParse() {
        ProtocolParser parser = new ProtocolParser();
        
        byte[] key = "testKey".getBytes();
        byte[] val = "testVal".getBytes();
        
        // Construct frame
        ByteBuffer buffer = ByteBuffer.allocateDirect(100);
        buffer.put(Config.MAGIC_NUMBER);
        buffer.put(Command.PUT.getCode());
        buffer.putInt(key.length);
        buffer.putInt(val.length);
        buffer.put(key);
        buffer.put(val);
        buffer.flip();
        
        Request req = parser.parse(null, buffer);
        Assert.assertNotNull(req);
        Assert.assertEquals((long) Command.PUT.getCode(), (long) req.getCommand().getCode());
        Assert.assertEquals((long) key.length, (long) req.getKeyBuffer().remaining());
        Assert.assertEquals((long) val.length, (long) req.getValueBuffer().remaining());
    }
    
    public void testPartialFrameYieldsNull() {
        ProtocolParser parser = new ProtocolParser();
        
        ByteBuffer buffer = ByteBuffer.allocateDirect(100);
        buffer.put(Config.MAGIC_NUMBER);
        buffer.put(Command.PUT.getCode());
        buffer.flip(); // Only 2 bytes available, requires 10 for header
        
        Request req = parser.parse(null, buffer);
        Assert.assertNull(req); // Needs more data to construct a frame
    }
    
    public void testInvalidMagicNumberThrowsException() {
        ProtocolParser parser = new ProtocolParser();
        
        ByteBuffer buffer = ByteBuffer.allocateDirect(100);
        buffer.put((byte) 0x00); // INVALID MAGIC NUMBER
        buffer.put(Command.PUT.getCode());
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.flip(); 
        
        boolean threw = false;
        try {
            parser.parse(null, buffer);
        } catch (IllegalStateException e) {
            threw = true;
        }
        
        Assert.assertTrue(threw);
    }
}
