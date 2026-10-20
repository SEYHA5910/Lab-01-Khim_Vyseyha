package edu.itc.se.lab01.task2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


// 1. Interface
interface Counter {
    void increment();
    long get();
}

// 2. Unsafe Implementation
class UnsafeCounter implements Counter {
    private long count = 0;
    public void increment() { count++; }
    public long get() { return count; }
}

// 5a. Synchronized Methods
class SynchronizedCounter implements Counter {
    private long count = 0;
    public synchronized void increment() { count++; }
    public synchronized long get() { return count; }
}

// 5b. ReentrantLock
class LockCounter implements Counter {
    private long count = 0;
    private final Lock lock = new ReentrantLock();
    
    public void increment() {
        lock.lock();
        try { count++; } finally { lock.unlock(); }
    }
    
    public long get() {
        lock.lock();
        try { return count; } finally { lock.unlock(); }
    }
}

// 5c. AtomicLong
class AtomicCounter implements Counter {
    private final AtomicLong count = new AtomicLong(0);
    public void increment() { count.incrementAndGet(); }
    public long get() { return count.get(); }
}

class LongAdderCounter implements Counter {
    private final LongAdder count = new LongAdder();
    public void increment() { count.increment(); }
    public long get() { return count.sum(); }
}

public class Bench {
    
    static final int THREADS = 8;
    static final int ITERATIONS = 1_000_000;
    static void run(String label, Counter counter) throws InterruptedException {
        long start = System.nanoTime();
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            Thread th = new Thread(() -> {
                for (int i = 0; i < ITERATIONS; i++) counter.increment();
            }, "inc-" + t);
            threads.add(th);
            th.start();
        }
        for (Thread th : threads) th.join();
        long ms = (System.nanoTime() - start) / 1_000_000;
        long expected = (long) THREADS * ITERATIONS;
        System.out.printf("%-22s value=%,d  correct=%-5b  %d ms%n",
                label, counter.get(), counter.get() == expected, ms);
}

 public static void main(String[] args) throws InterruptedException {
        run("UnsafeCounter", new UnsafeCounter());
        run("SynchronizedCounter", new SynchronizedCounter());
        run("LockCounter", new LockCounter());
        run("AtomicCounter", new AtomicCounter());
        run("LongAdderCounter", new LongAdderCounter());

        // TODO: SynchronizedCounter, LockCounter, AtomicCounter, LongAdderCounter
    }
}
