# AegisKV

AegisKV is a high-performance, distributed key-value store built entirely in Java. It features a custom binary TCP protocol, an off-heap storage engine, durable write-ahead logging (WAL), and high availability through Raft-based consensus.

## Features
- **Raw TCP Binary Protocol**: Optimized for low-latency, high-throughput communication without HTTP overhead.
- **Off-Heap Storage Engine**: Bypasses Java garbage collection pauses for predictable, microsecond-accurate data retrieval.
- **Raft Consensus Mesh**: Ensures fault-tolerance and high availability by automatically electing leaders and replicating data across the cluster.
- **Chaos-Resilient**: Gracefully handles simulated node crashes and network disruptions with seamless client failovers.
- **Zero-Dependency Benchmark**: Ships with a custom benchmarking harness capable of blasting the cluster with over 1M ops/sec.

## Getting Started

### Prerequisites
- Java JDK 11 or higher
- PowerShell or standard Linux/Mac terminal

### Compiling the Source
Compile the main server application code:
```powershell
# For Windows PowerShell
Get-ChildItem -Path src\main\java -Filter *.java -Recurse | Select-Object -ExpandProperty FullName | Out-File sources.txt -Encoding Ascii
javac -d out "@sources.txt"

# For Linux / Mac
javac -d out $(find src/main/java -name "*.java")
```

### Booting the Cluster
To see the Raft consensus and failover in action, start three instances of AegisKV on different ports. Each node will maintain its own isolated Write-Ahead Log (`data/aegiskv_<port>.wal`).

Open three separate terminals and run:

**Node 1 (Leader/Follower):**
```bash
java -cp out com.aegiskv.AegisKVServer 8001 8002,8003
```
**Node 2 (Follower):**
```bash
java -cp out com.aegiskv.AegisKVServer 8002 8001,8003
```
**Node 3 (Follower):**
```bash
java -cp out com.aegiskv.AegisKVServer 8003 8001,8002
```

---

## Running the Benchmark & Chaos Test

The repository includes a standalone `Benchmark.java` client designed to stress test the cluster and demonstrate its resilience.

### Compiling the Benchmark
```powershell
javac Benchmark.java
```

### Standard Throughput Test
Run the standard baseline test to measure pure, steady-state throughput:
```powershell
java Benchmark
```

### Chaos Engineering Failover Test
Run the benchmark with the `--enable-chaos` flag. This will establish steady-state traffic for 5 seconds and then fire a programmatic poison-pill command to Node 8001, killing it instantly. 

```powershell
java Benchmark --enable-chaos
```

Observe the terminal as the client threads seamlessly drop the dead connections, back off to wait for AegisKV to elect a new Raft leader, and automatically failover to Nodes 8002 and 8003 without dropping a single byte!
