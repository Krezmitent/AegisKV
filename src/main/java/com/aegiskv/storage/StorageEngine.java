package com.aegiskv.storage;

import com.aegiskv.consensus.RaftNode;
import com.aegiskv.consensus.Role;
import com.aegiskv.network.Command;
import com.aegiskv.network.Request;
import com.aegiskv.wal.WriteAheadLog;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class StorageEngine {
    private static final Logger LOGGER = Logger.getLogger(StorageEngine.class.getName());
    
    private final OffHeapMemoryManager memoryManager;
    private final IndexMap indexMap;
    private final WriteAheadLog wal;
    private RaftNode raftNode; // Intercepts mutations for consensus

    public StorageEngine(int capacityMB, WriteAheadLog wal) {
        this.memoryManager = new OffHeapMemoryManager(capacityMB);
        this.indexMap = new IndexMap();
        this.wal = wal;
    }
    
    public void setRaftNode(RaftNode raftNode) {
        this.raftNode = raftNode;
    }

    public void execute(Request request) {
        ByteBuffer keyBuffer = request.getKeyBuffer();
        
        switch (request.getCommand()) {
            case SYNC:
                // Inter-cluster communication for Consensus
                handleSync(request);
                break;
                
            case PUT:
                if (raftNode != null && raftNode.getRole() != Role.LEADER) {
                    LOGGER.warning("Write Rejected: Node is not the LEADER");
                    return;
                }
                
                ByteBuffer valueBuffer = request.getValueBuffer();
                try {
                    wal.appendPut(keyBuffer, valueBuffer);
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Failed to write PUT to WAL", e);
                    return;
                }
                
                int length = valueBuffer.remaining();
                int offset = memoryManager.allocateAndWrite(valueBuffer);
                
                if (offset != -1) {
                    indexMap.put(keyBuffer, offset, length);
                    LOGGER.fine("PUT successful");
                    // Replication to followers happens next in a full Raft setup
                } else {
                    LOGGER.warning("PUT failed due to OOM");
                }
                break;
                
            case GET:
                // FOLLOWER reads are permitted but may be stale
                Long encodedAddress = indexMap.get(keyBuffer);
                if (encodedAddress != null) {
                    int readOffset = IndexMap.extractOffset(encodedAddress);
                    int readLength = IndexMap.extractLength(encodedAddress);
                    
                    ByteBuffer result = memoryManager.readDirect(readOffset, readLength);
                    LOGGER.fine("GET successful, returned " + readLength + " bytes");
                } else {
                    LOGGER.fine("GET failed, key not found");
                }
                break;
                
            case DELETE:
                if (raftNode != null && raftNode.getRole() != Role.LEADER) {
                    LOGGER.warning("Write Rejected: Node is not the LEADER");
                    return;
                }
                
                try {
                    wal.appendDelete(keyBuffer);
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Failed to write DELETE to WAL", e);
                    return;
                }
                
                Long removed = indexMap.remove(keyBuffer);
                if (removed != null) {
                    LOGGER.fine("DELETE successful");
                } else {
                    LOGGER.fine("DELETE failed, key not found");
                }
                break;
                
            default:
                LOGGER.warning("Unsupported command: " + request.getCommand());
        }
    }
    
    private void handleSync(Request request) {
        if (raftNode == null) return;
        
        ByteBuffer keyBuffer = request.getKeyBuffer();
        if (keyBuffer != null && keyBuffer.remaining() >= 5) {
            byte opCode = keyBuffer.get();
            int term = keyBuffer.getInt();
            
            // Simplified metadata extraction (0x01 AppendEntries, 0x02 RequestVote)
            if (opCode == 0x01) {
                raftNode.getReplicator().handleAppendEntries(term, "peer");
            } else if (opCode == 0x02) {
                raftNode.getReplicator().handleRequestVote(term, "peer");
            }
        }
    }
    
    public void recoverPut(ByteBuffer keyBuffer, ByteBuffer valueBuffer) {
        int length = valueBuffer.remaining();
        int offset = memoryManager.allocateAndWrite(valueBuffer);
        if (offset != -1) {
            indexMap.put(keyBuffer, offset, length);
        }
    }
    
    public void recoverDelete(ByteBuffer keyBuffer) {
        indexMap.remove(keyBuffer);
    }
}
