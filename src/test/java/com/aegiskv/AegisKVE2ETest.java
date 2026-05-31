package com.aegiskv;

import com.aegiskv.network.Command;
import com.aegiskv.test.Assert;
import com.aegiskv.util.Config;

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class AegisKVE2ETest {
    
    public void testFullPipeline() throws Exception {
        // 1. Clean up stale WAL
        File walFile = new File(Config.WAL_PATH);
        if (walFile.exists()) {
            walFile.delete();
        }

        // 2. Boot the full cluster server node in a background daemon thread
        Thread serverThread = new Thread(() -> AegisKVServer.main(new String[]{}));
        serverThread.setDaemon(true);
        serverThread.start();
        
        // Wait for Reactor and Raft Node to fully initialize and elect itself leader
        Thread.sleep(1500);
        
        // 3. Connect a raw NIO Socket Client
        SocketChannel channel = SocketChannel.open(new InetSocketAddress("127.0.0.1", Config.DEFAULT_PORT));
        channel.configureBlocking(true);
        
        // 4. Construct a binary PUT frame according to our custom protocol spec
        byte[] key = "e2eKey".getBytes();
        byte[] val = "e2eValue".getBytes();
        
        ByteBuffer frame = ByteBuffer.allocateDirect(100);
        frame.put(Config.MAGIC_NUMBER);
        frame.put(Command.PUT.getCode());
        frame.putInt(key.length);
        frame.putInt(val.length);
        frame.put(key);
        frame.put(val);
        frame.flip();
        
        // 5. Blast it over the wire
        while(frame.hasRemaining()) {
            channel.write(frame);
        }
        
        // Give the Reactor WorkerPool a moment to asynchronously dispatch to StorageEngine + WAL
        Thread.sleep(1000);
        
        channel.close();
        
        // 6. Assert WAL file was successfully created, proving the request breached all layers
        Assert.assertTrue(new File(Config.WAL_PATH).exists());
        Assert.assertTrue(new File(Config.WAL_PATH).length() > 0);
    }
}
