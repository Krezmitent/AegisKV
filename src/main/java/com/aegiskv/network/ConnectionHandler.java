package com.aegiskv.network;

import com.aegiskv.util.Config;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ConnectionHandler {
    private static final Logger LOGGER = Logger.getLogger(ConnectionHandler.class.getName());
    
    private final SocketChannel socketChannel;
    private final WorkerPool workerPool;
    private final ByteBuffer readBuffer;
    private final ProtocolParser protocolParser;
    
    public ConnectionHandler(SocketChannel socketChannel, WorkerPool workerPool) {
        this.socketChannel = socketChannel;
        this.workerPool = workerPool;
        this.readBuffer = ByteBuffer.allocateDirect(Config.NETWORK_BUFFER_SIZE);
        this.protocolParser = new ProtocolParser();
    }
    
    public void read(SelectionKey key) {
        try {
            int bytesRead = socketChannel.read(readBuffer);
            if (bytesRead == -1) {
                closeConnection(key);
                return;
            }
            
            readBuffer.flip();
            processBuffer();
            readBuffer.compact();
            
        } catch (IOException e) {
            closeConnection(key);
        } catch (IllegalStateException e) {
            LOGGER.log(Level.WARNING, "Protocol error, dropping connection", e);
            closeConnection(key);
        }
    }
    
    private void processBuffer() {
        while (readBuffer.remaining() > 0) {
            Request request = protocolParser.parse(socketChannel, readBuffer);
            if (request == null) {
                // Incomplete frame, wait for more data in the next selection cycle
                break;
            }
            workerPool.submit(request);
        }
    }
    
    public void write(SelectionKey key) {
        // Will be used for async responses in later phases
        key.interestOps(SelectionKey.OP_READ);
    }
    
    private void closeConnection(SelectionKey key) {
        key.cancel();
        try {
            if (socketChannel != null && socketChannel.isOpen()) {
                socketChannel.close();
            }
        } catch (IOException ignored) {}
    }
}
