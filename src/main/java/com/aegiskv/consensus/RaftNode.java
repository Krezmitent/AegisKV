package com.aegiskv.consensus;

import com.aegiskv.storage.StorageEngine;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public final class RaftNode {
    private static final Logger LOGGER = Logger.getLogger(RaftNode.class.getName());
    
    private volatile Role role = Role.FOLLOWER;
    private final AtomicInteger currentTerm = new AtomicInteger(0);
    private volatile String votedFor = null;
    
    private final ClusterConfig config;
    private final ElectionManager electionManager;
    private final Replicator replicator;
    private final StorageEngine storageEngine;

    public RaftNode(ClusterConfig config, StorageEngine storageEngine) {
        this.config = config;
        this.storageEngine = storageEngine;
        this.replicator = new Replicator(this, config);
        this.electionManager = new ElectionManager(this);
    }
    
    public void start() {
        electionManager.startElectionTimer();
        LOGGER.info("Raft Node initialized. Started as FOLLOWER on term " + currentTerm.get());
    }

    public synchronized void becomeCandidate() {
        if (role == Role.LEADER) return;
        role = Role.CANDIDATE;
        currentTerm.incrementAndGet();
        votedFor = config.getLocalNodeId();
        LOGGER.info("Transitioned to CANDIDATE for term " + currentTerm.get());
        
        // Single node cluster auto-elects itself
        if (config.getPeers().isEmpty()) {
            becomeLeader();
        } else {
            replicator.broadcastRequestVote(currentTerm.get(), config.getLocalNodeId());
        }
    }

    public synchronized void becomeLeader() {
        if (role == Role.LEADER) return;
        role = Role.LEADER;
        LOGGER.info("Elected as LEADER for term " + currentTerm.get() + ". Establishing dominance.");
        electionManager.stopElectionTimer();
        electionManager.startHeartbeatTimer();
    }
    
    public synchronized void stepDown(int newTerm) {
        currentTerm.set(newTerm);
        role = Role.FOLLOWER;
        votedFor = null;
        electionManager.resetElectionTimer();
        LOGGER.info("Stepped down to FOLLOWER on term " + newTerm);
    }
    
    public Role getRole() {
        return role;
    }
    
    public int getCurrentTerm() {
        return currentTerm.get();
    }
    
    public String getVotedFor() {
        return votedFor;
    }
    
    public void setVotedFor(String candidateId) {
        this.votedFor = candidateId;
    }
    
    public Replicator getReplicator() {
        return replicator;
    }
    
    public ElectionManager getElectionManager() {
        return electionManager;
    }
}
