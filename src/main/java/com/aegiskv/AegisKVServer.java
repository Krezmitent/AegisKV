package com.aegiskv;

import com.aegiskv.consensus.ClusterConfig;
import com.aegiskv.consensus.RaftNode;
import com.aegiskv.network.Reactor;
import com.aegiskv.storage.StorageEngine;
import com.aegiskv.util.Config;
import com.aegiskv.wal.RecoveryManager;
import com.aegiskv.wal.WriteAheadLog;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AegisKVServer {
    private static final Logger LOGGER = Logger.getLogger(AegisKVServer.class.getName());

    public static void main(String[] args) {
        int port = Config.DEFAULT_PORT;
        String walPath = Config.WAL_PATH;
        List<InetSocketAddress> peers = new ArrayList<>();

        if (args.length >= 1) {
            port = Integer.parseInt(args[0]);
            walPath = "data/aegiskv_" + port + ".wal"; // Use unique WAL for each node
        }
        if (args.length >= 2) {
            for (String p : args[1].split(",")) {
                peers.add(new InetSocketAddress("127.0.0.1", Integer.parseInt(p)));
            }
        }

        LOGGER.info("Starting AegisKV Distributed Store on port " + port + "...");
        try {
            WriteAheadLog wal = new WriteAheadLog(walPath);
            StorageEngine storageEngine = new StorageEngine(256, wal);
            
            // Replay WAL
            RecoveryManager.recover(wal, storageEngine);
            
            // Initialize Raft Mesh
            String localNodeId = "Node-" + port;
            
            ClusterConfig clusterConfig = new ClusterConfig(localNodeId, peers);
            RaftNode raftNode = new RaftNode(clusterConfig, storageEngine);
            storageEngine.setRaftNode(raftNode);
            
            // Ignite Consensus Timers
            raftNode.start();
            
            Reactor reactor = new Reactor(port, storageEngine);
            Thread reactorThread = new Thread(reactor, "AegisKV-Reactor-" + port);
            reactorThread.start();
            
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LOGGER.info("Received shutdown signal. Stopping AegisKV...");
                raftNode.getElectionManager().stopElectionTimer();
                raftNode.getElectionManager().stopHeartbeatTimer();
                reactor.stop();
                try {
                    reactorThread.join(5000);
                    wal.close();
                } catch (InterruptedException | IOException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            
            LOGGER.info("AegisKV fully operational on port " + port);
            
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to start AegisKV", e);
            System.exit(1);
        }
    }
}
