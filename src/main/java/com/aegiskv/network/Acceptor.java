package com.aegiskv.network;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public final class Acceptor {
    private final Selector selector;
    private final WorkerPool workerPool;

    public Acceptor(Selector selector, WorkerPool workerPool) {
        this.selector = selector;
        this.workerPool = workerPool;
    }

    public void accept(SelectionKey key) throws IOException {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        SocketChannel socketChannel = serverSocketChannel.accept();
        if (socketChannel != null) {
            socketChannel.configureBlocking(false);
            
            // Disable Nagle's algorithm for lower latency
            socketChannel.socket().setTcpNoDelay(true);
            socketChannel.socket().setKeepAlive(true);
            
            ConnectionHandler handler = new ConnectionHandler(socketChannel, workerPool);
            socketChannel.register(selector, SelectionKey.OP_READ, handler);
        }
    }
}
