package com.aegiskv.network;

import com.aegiskv.storage.StorageEngine;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Reactor implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(Reactor.class.getName());
    
    private final Selector selector;
    private final ServerSocketChannel serverSocketChannel;
    private final Acceptor acceptor;
    private final WorkerPool workerPool;
    private volatile boolean running = true;

    public Reactor(int port, StorageEngine storageEngine) throws IOException {
        this.selector = Selector.open();
        this.serverSocketChannel = ServerSocketChannel.open();
        this.serverSocketChannel.socket().bind(new InetSocketAddress(port));
        this.serverSocketChannel.configureBlocking(false);
        this.serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        
        this.workerPool = new WorkerPool(storageEngine);
        this.acceptor = new Acceptor(selector, workerPool);
        
        LOGGER.info("Reactor initialized, listening on port " + port);
    }

    @Override
    public void run() {
        LOGGER.info("Reactor event loop started.");
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                if (selector.select() == 0) {
                    continue;
                }
                
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectedKeys.iterator();
                
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();
                    
                    if (!key.isValid()) {
                        continue;
                    }
                    
                    try {
                        if (key.isAcceptable()) {
                            acceptor.accept(key);
                        } else if (key.isReadable()) {
                            ConnectionHandler handler = (ConnectionHandler) key.attachment();
                            handler.read(key);
                        } else if (key.isWritable()) {
                            ConnectionHandler handler = (ConnectionHandler) key.attachment();
                            handler.write(key);
                        }
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Error handling selection key, closing channel.", e);
                        key.cancel();
                    }
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Reactor selector error", e);
            }
        }
        shutdown();
    }
    
    public void stop() {
        running = false;
        selector.wakeup();
    }
    
    private void shutdown() {
        try {
            selector.close();
            serverSocketChannel.close();
            workerPool.shutdown();
            LOGGER.info("Reactor gracefully shut down.");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error during reactor shutdown", e);
        }
    }
}
