package com.aegiskv.util;

public final class Config {
    public static final int DEFAULT_PORT = 7070;
    public static final int MAX_WORKER_THREADS = Runtime.getRuntime().availableProcessors() * 2;
    public static final int NETWORK_BUFFER_SIZE = 8192;
    public static final byte MAGIC_NUMBER = (byte) 0xAE;
    
    // Path to the durable write-ahead log
    public static final String WAL_PATH = "data/aegiskv.wal";
    
    private Config() {
        // Prevent instantiation
    }
}
