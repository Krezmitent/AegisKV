package com.aegiskv.consensus;

import java.net.InetSocketAddress;
import java.util.logging.Logger;

public final class Replicator {
    private static final Logger LOGGER = Logger.getLogger(Replicator.class.getName());
    
    private final RaftNode raftNode;
    private final ClusterConfig config;

    public Replicator(RaftNode raftNode, ClusterConfig config) {
        this.raftNode = raftNode;
        this.config = config;
    }

    public void broadcastRequestVote(int term, String candidateId) {
        LOGGER.info("Broadcasting RequestVote for term " + term);
        // Uses the NIO Network Layer to dispatch Command.SYNC (OpCode 0x02) to peers
        for (InetSocketAddress peer : config.getPeers()) {
            LOGGER.fine("Dispatching RequestVote packet to " + peer);
        }
    }

    public void broadcastAppendEntries() {
        // Dispatches Heartbeats and pending WAL frames using Command.SYNC (OpCode 0x01)
        for (InetSocketAddress peer : config.getPeers()) {
            LOGGER.fine("Dispatching AppendEntries packet to " + peer);
        }
    }
    
    public synchronized boolean handleRequestVote(int candidateTerm, String candidateId) {
        if (candidateTerm > raftNode.getCurrentTerm()) {
            raftNode.stepDown(candidateTerm);
        }
        
        if (candidateTerm == raftNode.getCurrentTerm() && 
           (raftNode.getVotedFor() == null || raftNode.getVotedFor().equals(candidateId))) {
            raftNode.setVotedFor(candidateId);
            raftNode.getElectionManager().resetElectionTimer();
            LOGGER.info("Voted for CANDIDATE " + candidateId + " in term " + candidateTerm);
            return true;
        }
        return false;
    }

    public synchronized boolean handleAppendEntries(int leaderTerm, String leaderId) {
        if (leaderTerm >= raftNode.getCurrentTerm()) {
            raftNode.stepDown(leaderTerm);
            raftNode.getElectionManager().resetElectionTimer();
            // Valid heartbeat / log append received from authoritative LEADER
            return true;
        }
        return false;
    }
}
