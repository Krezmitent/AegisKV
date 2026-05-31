import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;

public class Benchmark {
    private static final int[] PORTS = {8001, 8002, 8003};
    private static final byte MAGIC = (byte) 0xAE;
    private static final byte CMD_PUT = 0x01;
    private static final byte CMD_GET = 0x02;
    private static final byte CMD_SHUTDOWN = (byte) 0xFF;

    private static final int NUM_THREADS = 64;
    private static final int DURATION_SEC = 30;
    // Pre-allocate large array per thread to prevent GC pauses on hot path.
    // 5 million per thread * 64 threads should be plenty for 30s.
    private static final int MAX_OPS_PER_THREAD = 5_000_000;

    // Shared state
    private static final AtomicLong totalOps = new AtomicLong(0);
    private static final AtomicBoolean isRunning = new AtomicBoolean(true);
    private static final CountDownLatch startLatch = new CountDownLatch(1);
    private static final AtomicInteger activeThreads = new AtomicInteger(NUM_THREADS);
    
    // Using 2D array: threadId -> latencies to avoid synchronization and object allocation overhead
    private static final long[][] latencies = new long[NUM_THREADS][MAX_OPS_PER_THREAD];
    private static final AtomicIntegerArray opCounts = new AtomicIntegerArray(NUM_THREADS);
    private static final AtomicBoolean connectionErrorPrinted = new AtomicBoolean(false);

    public static void main(String[] args) throws Exception {
        boolean chaos = Arrays.asList(args).contains("--enable-chaos");

        System.out.println("Starting AegisKV Benchmark...");
        System.out.println("Chaos Engineering: " + (chaos ? "ENABLED" : "DISABLED"));
        System.out.println("Warming up clients...");

        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);

        for (int i = 0; i < NUM_THREADS; i++) {
            final int threadId = i;
            executor.submit(() -> workerLoop(threadId));
        }

        Thread dashboardThread = new Thread(Benchmark::runDashboard);
        dashboardThread.setDaemon(true);
        dashboardThread.start();

        if (chaos) {
            Thread chaosThread = new Thread(Benchmark::triggerChaos);
            chaosThread.setDaemon(true);
            chaosThread.start();
        }

        // Release the hounds! Start all threads simultaneously
        startLatch.countDown();

        Thread.sleep(DURATION_SEC * 1000L);
        isRunning.set(false);

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        
        System.out.println("\n[AegisKV Benchmark Complete]");
        printFinalMetrics();
        System.exit(0);
    }

    private static void workerLoop(int threadId) {
        // Pre-allocate keys to avoid object creation during benchmark
        byte[][] keys = new byte[1000][];
        for (int i = 0; i < 1000; i++) {
            keys[i] = ("bench-key-" + i).getBytes();
        }
        byte[] valBytes = "bench-value-payload-data-padding-1234567890".getBytes();

        int currentPortIndex = threadId % PORTS.length; // distribute initial connections
        Socket socket = null;
        InputStream in = null;
        OutputStream out = null;
        
        int ops = 0;
        long[] threadLatencies = latencies[threadId];

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        byte[] readBuffer = new byte[1024];

        try {
            startLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            activeThreads.decrementAndGet();
            return;
        }

        while (isRunning.get() && ops < MAX_OPS_PER_THREAD) {
            if (socket == null || socket.isClosed()) {
                try {
                    socket = new Socket("127.0.0.1", PORTS[currentPortIndex]);
                    socket.setTcpNoDelay(true); // Disable Nagle's algorithm for microsecond accuracy
                    in = socket.getInputStream();
                    out = socket.getOutputStream();
                } catch (Exception e) {
                    if (connectionErrorPrinted.compareAndSet(false, true)) {
                        System.err.println("\n[ERROR] Initial connection failed. Is AegisKV running on port " + PORTS[currentPortIndex] + "? Error: " + e.getMessage() + " (suppressing further connection errors)");
                    }
                    currentPortIndex = (currentPortIndex + 1) % PORTS.length;
                    try {
                        Thread.sleep(100); // Backoff for node availability/raft election
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue; 
                }
            }

            boolean isPut = ThreadLocalRandom.current().nextInt(100) < 20; // 80/20 Read/Write Ratio
            byte cmd = isPut ? CMD_PUT : CMD_GET;
            byte[] keyBytes = keys[ops % 1000];
            
            buffer.clear();
            buffer.put(MAGIC);
            buffer.put(cmd);
            buffer.putInt(keyBytes.length);
            
            if (isPut) {
                buffer.putInt(valBytes.length);
                buffer.put(keyBytes);
                buffer.put(valBytes);
            } else {
                buffer.putInt(0);
                buffer.put(keyBytes);
            }

            long start = System.nanoTime();
            try {
                out.write(buffer.array(), 0, buffer.position());
                out.flush();

                // Note: AegisKV Phase 1 does not currently send responses back over the socket.
                // We operate in fire-and-forget mode to blast throughput without hanging.
                
                long end = System.nanoTime();
                long latencyUs = (end - start) / 1000L;
                
                threadLatencies[ops++] = latencyUs;
                opCounts.set(threadId, ops);
                totalOps.incrementAndGet();

            } catch (Exception e) {
                if (connectionErrorPrinted.compareAndSet(false, true)) {
                    System.err.println("\n[ERROR] Request failed on port " + PORTS[currentPortIndex] + ". Error: " + e.getMessage() + " (suppressing further request errors)");
                }
                // Connection dropped or failed! Discard socket and trigger failover behavior
                try {
                    if (socket != null) socket.close();
                } catch (Exception ignored) {}
                socket = null;
                currentPortIndex = (currentPortIndex + 1) % PORTS.length;
                try {
                    Thread.sleep(50); // Small pause for Raft leader election
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        activeThreads.decrementAndGet();
    }

    private static void runDashboard() {
        long startTime = System.currentTimeMillis();
        long lastOps = 0;

        while (isRunning.get()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            long currentOps = totalOps.get();
            long opsPerSec = currentOps - lastOps;
            lastOps = currentOps;

            long elapsed = (System.currentTimeMillis() - startTime) / 1000L;
            
            // Dynamic estimation of latencies from Thread 0
            long p50 = 0, p99 = 0;
            int thread0Ops = opCounts.get(0);
            if (thread0Ops > 100) {
                int sampleSize = Math.min(1000, thread0Ops);
                long[] sample = new long[sampleSize];
                System.arraycopy(latencies[0], thread0Ops - sampleSize, sample, 0, sampleSize);
                Arrays.sort(sample);
                p50 = sample[(int)(sampleSize * 0.5)];
                p99 = sample[(int)(sampleSize * 0.99)];
            }

            System.out.println("\n[AegisKV Benchmark Progress]");
            System.out.printf("Elapsed Time: %ds / %ds\n", elapsed, DURATION_SEC);
            System.out.printf("Current Throughput: %,d ops/sec\n", opsPerSec);
            System.out.printf("P50 Latency: %d micros | P99 Latency: %d micros\n", p50, p99);
            System.out.printf("Active Client Threads: %d\n", activeThreads.get());
        }
    }
    
    private static void triggerChaos() {
        try {
            Thread.sleep(5000); // Wait exactly 5 seconds until steady state
            System.out.println("\n[CHAOS MONKEY] Unleashing chaos... Sending poison-pill to Leader on port 8001!");
            
            try (Socket s = new Socket("127.0.0.1", 8001)) {
                OutputStream out = s.getOutputStream();
                ByteBuffer buffer = ByteBuffer.allocate(10);
                buffer.put(MAGIC);
                buffer.put(CMD_SHUTDOWN);
                buffer.putInt(0); // key len = 0
                buffer.putInt(0); // val len = 0
                out.write(buffer.array(), 0, buffer.position());
                out.flush();
                System.out.println("[CHAOS MONKEY] Poison-pill delivered!");
            } catch (Exception e) {
                System.out.println("[CHAOS MONKEY] Failed to connect to 8001. Already dead?");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void printFinalMetrics() {
        int totalRecorded = 0;
        for (int i = 0; i < NUM_THREADS; i++) {
            totalRecorded += opCounts.get(i);
        }

        if (totalRecorded == 0) {
            System.out.println("No operations were recorded.");
            return;
        }

        long[] allLatencies = new long[totalRecorded];
        int destPos = 0;
        for (int i = 0; i < NUM_THREADS; i++) {
            int count = opCounts.get(i);
            System.arraycopy(latencies[i], 0, allLatencies, destPos, count);
            destPos += count;
        }

        System.out.println("Sorting dataset for percentile calculations...");
        Arrays.sort(allLatencies);

        long min = allLatencies[0];
        long max = allLatencies[totalRecorded - 1];
        long p50 = allLatencies[(int) (totalRecorded * 0.50)];
        long p90 = allLatencies[(int) (totalRecorded * 0.90)];
        long p99 = allLatencies[(int) (totalRecorded * 0.99)];
        long p999 = allLatencies[(int) (totalRecorded * 0.999)];
        
        long totalOpsCount = totalOps.get();
        double totalThroughput = (double) totalOpsCount / DURATION_SEC;

        System.out.printf("\n--- AegisKV Benchmark Results ---\n");
        System.out.printf("Total Operations: %,d\n", totalOpsCount);
        System.out.printf("Average Throughput: %,.2f ops/sec\n", totalThroughput);
        System.out.println("--- Latency Percentiles (micros) ---");
        System.out.printf("Min:    %d micros\n", min);
        System.out.printf("P50:    %d micros\n", p50);
        System.out.printf("P90:    %d micros\n", p90);
        System.out.printf("P99:    %d micros\n", p99);
        System.out.printf("P99.9:  %d micros\n", p999);
        System.out.printf("Max:    %d micros\n", max);
    }
}
