package com.aegiskv.consensus;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public final class ElectionManager {
    private static final Logger LOGGER = Logger.getLogger(ElectionManager.class.getName());
    
    private final RaftNode raftNode;
    private final ScheduledExecutorService scheduler;
    private final Random random = new Random();
    
    private ScheduledFuture<?> electionTimeoutFuture;
    private ScheduledFuture<?> heartbeatFuture;

    public ElectionManager(RaftNode raftNode) {
        this.raftNode = raftNode;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread t = new Thread(runnable, "AegisKV-ElectionTimer");
            t.setDaemon(true);
            return t;
        });
    }

    public synchronized void startElectionTimer() {
        stopElectionTimer();
        // Randomized timeouts to avoid split votes (150ms - 300ms)
        int timeoutMs = 150 + random.nextInt(150);
        electionTimeoutFuture = scheduler.schedule(this::onElectionTimeout, timeoutMs, TimeUnit.MILLISECONDS);
    }

    public synchronized void resetElectionTimer() {
        if (raftNode.getRole() != Role.LEADER) {
            startElectionTimer();
        }
    }
    
    public synchronized void stopElectionTimer() {
        if (electionTimeoutFuture != null) {
            electionTimeoutFuture.cancel(false);
            electionTimeoutFuture = null;
        }
    }

    private void onElectionTimeout() {
        if (raftNode.getRole() != Role.LEADER) {
            LOGGER.info("Election timeout reached without hearing from Leader. Initiating election.");
            raftNode.becomeCandidate();
            startElectionTimer(); // Restart timer in case this election results in split vote
        }
    }

    public synchronized void startHeartbeatTimer() {
        stopHeartbeatTimer();
        // Heartbeats must be faster than the minimum election timeout
        heartbeatFuture = scheduler.scheduleAtFixedRate(this::sendHeartbeat, 0, 50, TimeUnit.MILLISECONDS);
    }
    
    public synchronized void stopHeartbeatTimer() {
        if (heartbeatFuture != null) {
            heartbeatFuture.cancel(false);
            heartbeatFuture = null;
        }
    }

    private void sendHeartbeat() {
        if (raftNode.getRole() == Role.LEADER) {
            raftNode.getReplicator().broadcastAppendEntries();
        }
    }
}
