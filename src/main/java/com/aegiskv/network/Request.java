package com.aegiskv.network;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public final class Request {
    private final SocketChannel clientChannel;
    private final Command command;
    private final ByteBuffer keyBuffer;
    private final ByteBuffer valueBuffer;

    public Request(SocketChannel clientChannel, Command command, ByteBuffer keyBuffer, ByteBuffer valueBuffer) {
        this.clientChannel = clientChannel;
        this.command = command;
        this.keyBuffer = keyBuffer;
        this.valueBuffer = valueBuffer;
    }

    public SocketChannel getClientChannel() {
        return clientChannel;
    }

    public Command getCommand() {
        return command;
    }

    public ByteBuffer getKeyBuffer() {
        return keyBuffer;
    }

    public ByteBuffer getValueBuffer() {
        return valueBuffer;
    }
}
