package edu.itc.se.lab01.task5;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

// --- FIX B & GRACEFUL SHUTDOWN ---
public class Main {
    static final class Account {
        final int id; private long balance;
        final Lock lock = new ReentrantLock(); // Required for tryLock
        
        Account(int id, long balance) { this.id = id; this.balance = balance; }
        long balance() { return balance; }
        void withdraw(long a) { balance -= a; }
        void deposit(long a)  { balance += a; }
    }

    static void transfer(Account from, Account to, long amount, AtomicInteger retries) throws InterruptedException {
        var rnd = ThreadLocalRandom.current();
        while (true) {
            if (from.lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                try {
                    if (to.lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                        try {
                            if (from.balance() >= amount) { from.withdraw(amount); to.deposit(amount); }
                            return; // Success
                        } finally {
                            to.lock.unlock();
                        }
                    }
                } finally {
                    from.lock.unlock();
                }
            }
            retries.incrementAndGet();
            Thread.sleep(rnd.nextInt(5, 15)); 
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Account[] accounts = new Account[4];
        for (int i = 0; i < accounts.length; i++) accounts[i] = new Account(i, 1_000);

        AtomicInteger retries = new AtomicInteger(0);
        AtomicLong totalTransfers = new AtomicLong(0);
        long start = System.currentTimeMillis();

        Thread[] workers = new Thread[8];
        for (int w = 0; w < workers.length; w++) {
            workers[w] = new Thread(() -> {
                var rnd = ThreadLocalRandom.current();
                long completed = 0;
                
                while (!Thread.currentThread().isInterrupted()) {
                    Account a = accounts[rnd.nextInt(accounts.length)];
                    Account b = accounts[rnd.nextInt(accounts.length)];
                    try {
                        if (a != b) {
                            transfer(a, b, rnd.nextInt(1, 50), retries);
                            completed++;
                            totalTransfers.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                System.out.println(Thread.currentThread().getName() + " stopped after " + completed + " transfers");
            }, "transfer-" + w);
            workers[w].start();
        }

        Thread.sleep(2000);
        System.out.println("\n[main] interrupting 8 workers after 2000 ms");
        for (Thread t : workers) t.interrupt();
        
        boolean allStopped = true;
        for (Thread t : workers) {
            t.join(1000); 
            if (t.isAlive()) allStopped = false;
        }

        long ms = System.currentTimeMillis() - start;
        long total = 0; for (Account a : accounts) total += a.balance();
        
        System.out.println("TryLockBank transfers=" + totalTransfers.get() + " retries=" + retries.get() + " total=" + total + " (expected 4000) in " + ms + " ms");
        System.out.println("all workers stopped=" + allStopped + " total=" + total + " (expected 4000)");
    }
}

// --- ORIGINAL DEADLOCKING BANK & WATCHDOG ---
class Bank {
    static final class Account {
        final int id; private long balance;
        Account(int id, long balance) { this.id = id; this.balance = balance; }
        long balance() { return balance; }
        void withdraw(long a) { balance -= a; }
        void deposit(long a)  { balance += a; }
    }

    static void transfer(Account from, Account to, long amount) {
        synchronized (from) {
            synchronized (to) {
                if (from.balance() >= amount) { from.withdraw(amount); to.deposit(amount); }
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Thread watchdog = new Thread(() -> {
            ThreadMXBean bean = ManagementFactory.getThreadMXBean();
            while (true) {
                long[] deadlocked = bean.findDeadlockedThreads();
                if (deadlocked != null) {
                    ThreadInfo[] infos = bean.getThreadInfo(deadlocked);
                    System.out.print("[watchdog] DEADLOCK detected between: ");
                    for (int i = 0; i < infos.length; i++) {
                        System.out.print(infos[i].getThreadName() + (i < infos.length - 1 ? ", " : ""));
                    }
                    System.out.println();
                    break;
                }
                try { Thread.sleep(1000); } catch (InterruptedException e) { break; }
            }
        });
        watchdog.setDaemon(true);
        watchdog.start();

        Account[] accounts = new Account[4];
        for (int i = 0; i < accounts.length; i++) accounts[i] = new Account(i, 1_000);

        Thread[] workers = new Thread[8];
        for (int w = 0; w < workers.length; w++) {
            workers[w] = new Thread(() -> {
                var rnd = ThreadLocalRandom.current();
                for (int i = 0; i < 100_000; i++) {
                    Account a = accounts[rnd.nextInt(accounts.length)];
                    Account b = accounts[rnd.nextInt(accounts.length)];
                    if (a != b) transfer(a, b, rnd.nextInt(1, 50));
                }
            }, "transfer-" + w);
            workers[w].start();
        }
        for (Thread t : workers) t.join();
    }
}

// --- FIX A: LOCK ORDERING ---
class OrderedBank {
    static final class Account {
        final int id; private long balance;
        Account(int id, long balance) { this.id = id; this.balance = balance; }
        long balance() { return balance; }
        void withdraw(long a) { balance -= a; }
        void deposit(long a)  { balance += a; }
    }

    static void transfer(Account from, Account to, long amount) {
        Account first = from.id < to.id ? from : to;
        Account second = from.id < to.id ? to : from;

        synchronized (first) {
            synchronized (second) {
                if (from.balance() >= amount) { from.withdraw(amount); to.deposit(amount); }
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Account[] accounts = new Account[4];
        for (int i = 0; i < accounts.length; i++) accounts[i] = new Account(i, 1_000);

        long start = System.currentTimeMillis();
        Thread[] workers = new Thread[8];
        for (int w = 0; w < workers.length; w++) {
            workers[w] = new Thread(() -> {
                var rnd = ThreadLocalRandom.current();
                for (int i = 0; i < 100_000; i++) {
                    Account a = accounts[rnd.nextInt(accounts.length)];
                    Account b = accounts[rnd.nextInt(accounts.length)];
                    if (a != b) transfer(a, b, rnd.nextInt(1, 50));
                }
            }, "transfer-" + w);
            workers[w].start();
        }
        for (Thread t : workers) t.join();
        
        long ms = System.currentTimeMillis() - start;
        long total = 0; for (Account a : accounts) total += a.balance();
        System.out.println("OrderedBank transfers=800000 total=" + total + " (expected 4000) in " + ms + " ms");
    }
}