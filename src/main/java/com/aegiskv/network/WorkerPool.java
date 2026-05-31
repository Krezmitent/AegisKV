package com.aegiskv.network;

import com.aegiskv.storage.StorageEngine;
import com.aegiskv.util.Config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public final class WorkerPool {
    private static final Logger LOGGER = Logger.getLogger(WorkerPool.class.getName());
    private final ExecutorService executor;
    private final StorageEngine storageEngine;

    public WorkerPool(StorageEngine storageEngine) {
        this.storageEngine = storageEngine;
        this.executor = Executors.newFixedThreadPool(Config.MAX_WORKER_THREADS, runnable -> {
            Thread t = new Thread(runnable, "AegisKV-Worker");
            t.setDaemon(true);
            return t;
        });
        LOGGER.info("Initialized WorkerPool with " + Config.MAX_WORKER_THREADS + " threads.");
    }

    public void submit(Request request) {
        executor.submit(() -> {
            try {
                // Route the network request directly into the Storage Engine
                storageEngine.execute(request);
            } catch (Exception e) {
                LOGGER.warning("Error processing request: " + e.getMessage());
            }
        });
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
