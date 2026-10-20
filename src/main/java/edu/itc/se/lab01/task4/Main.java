package edu.itc.se.lab01.task4;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class Main {
    static long countPrimes(long from, long to) {
        long count = 0;
        for (long n = Math.max(from, 2); n < to; n++) {
            boolean prime = true;
            for (long d = 2; d * d <= n; d++)
                if (n % d == 0) {
                    prime = false;
                    break;
                }
            if (prime)
                count++;
        }
        return count;
    }

    public static void main(String[] args) throws Exception {
        final long LIMIT = 4_000_000, CHUNKS = 16;

        // 1. Single-threaded CPU test
        long t0 = System.nanoTime();
        long single = countPrimes(0, LIMIT);
        System.out.printf("single-threaded: %d primes in %d ms%n", single, (System.nanoTime() - t0) / 1_000_000);

        // 2. Multi-threaded CPU test (Platform Threads)
        t0 = System.nanoTime();
        int cores = Runtime.getRuntime().availableProcessors();
        try (ExecutorService pool = Executors.newFixedThreadPool(cores)) {
            List<Future<Long>> futures = new ArrayList<>();
            long chunkSize = LIMIT / CHUNKS;

            for (int i = 0; i < CHUNKS; i++) {
                long start = i * chunkSize;
                long end = (i == CHUNKS - 1) ? LIMIT : (i + 1) * chunkSize;
                futures.add(pool.submit(new PrimeCounter(start, end)));
            }

            long total = 0;
            for (Future<Long> f : futures) {
                total += f.get(); // Waits for chunk to finish and adds result
            }
            System.out.printf("pool (%d threads): %d primes in %d ms%n", cores, total,
                    (System.nanoTime() - t0) / 1_000_000);
        }

        // 3. I/O-bound tests (Platform vs Virtual Threads)
        System.out.println("\nStarting I/O-bound tests...");

        try (ExecutorService platformPool = Executors.newFixedThreadPool(20)) {
            runDownloads(platformPool, "fixed(20) platform threads");
        }

        try (ExecutorService virtualPool = Executors.newVirtualThreadPerTaskExecutor()) {
            runDownloads(virtualPool, "virtual threads");
        }
    }

    private static void runDownloads(ExecutorService pool, String poolName) {
        long t0 = System.nanoTime();
        List<Future<String>> futures = new ArrayList<>();

        for (int i = 1; i <= 500; i++) {
            futures.add(pool.submit(new FakeDownload(i, "http://fake-url.com/file" + i)));
        }

        int ok = 0, failed = 0, timedOut = 0;
        for (Future<String> f : futures) {
            try {
                // Wait max 1 second for the download to finish
                f.get(1, TimeUnit.SECONDS);
                ok++;
            } catch (TimeoutException e) {
                f.cancel(true);
                timedOut++;
            } catch (ExecutionException e) {
                // ExecutionException wraps the original IOException thrown by the task
                failed++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        long time = (System.nanoTime() - t0) / 1_000_000;
        System.out.printf("downloads on %-29s: ok=%d failed=%d timedOut=%d in %d ms%n", poolName, ok, failed, timedOut,
                time);
    }
}

// Helper class for CPU-bound task
class PrimeCounter implements Callable<Long> {
    private final long from, to;

    public PrimeCounter(long from, long to) {
        this.from = from;
        this.to = to;
    }

    @Override
    public Long call() {
        return Main.countPrimes(from, to);
    }
}

// Helper class for I/O-bound task
class FakeDownload implements Callable<String> {
    private final int id;
    private final String url;

    public FakeDownload(int id, String url) {
        this.id = id;
        this.url = url;
    }

    @Override
    public String call() throws Exception {
        if (id == 1) {
            // 4. Timeout requirement: 1 task takes 5 seconds
            Thread.sleep(5000);
        } else if (id % 50 == 0) {
            // 3. Failure handling requirement: every 50th task crashes
            throw new IOException("boom");
        } else {
            // Normal task simulates network latency
            Thread.sleep(200);
        }
        return url + " -> " + 1024;
    }
}