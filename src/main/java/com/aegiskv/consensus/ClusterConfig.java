package com.aegiskv.consensus;

import java.net.InetSocketAddress;
import java.util.List;

public final class ClusterConfig {
    private final String localNodeId;
    private final List<InetSocketAddress> peers;

    public ClusterConfig(String localNodeId, List<InetSocketAddress> peers) {
        this.localNodeId = localNodeId;
        this.peers = peers;
    }

    public String getLocalNodeId() {
        return localNodeId;
    }

    public List<InetSocketAddress> getPeers() {
        return peers;
    }
}
