package edu.itc.se.lab01.task3;

import java.util.ArrayDeque;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        // Buffer capacity of 5
        OrderQueue queue = new BoundedBuffer(5); 
        AtomicInteger processedCount = new AtomicInteger(0);

        // 2 Producers x 50 orders
        // Only one producer sends the 3 poison pills to avoid cluttering the queue
        Thread p1 = new Thread(new Producer(queue, 50, 3), "producer-1");
        Thread p2 = new Thread(new Producer(queue, 50, 0), "producer-2");

        // 3 Consumers
        Thread c1 = new Thread(new Consumer(queue, processedCount), "consumer-1");
        Thread c2 = new Thread(new Consumer(queue, processedCount), "consumer-2");
        Thread c3 = new Thread(new Consumer(queue, processedCount), "consumer-3");

        long start = System.currentTimeMillis();

        p1.start(); p2.start();
        c1.start(); c2.start(); c3.start();

        p1.join(); p2.join();
        c1.join(); c2.join(); c3.join();

        long time = System.currentTimeMillis() - start;
        
        System.out.println("Total processed: " + processedCount.get() + " (expected 100) in " + time + " ms");
        
        // Assert the total processed is exactly 100
        assert processedCount.get() == 100 : "Failed: Expected 100 but got " + processedCount.get();
    }
}

// 1. Order Record
record Order(int id, String item, int qty) {}

// 2. Queue Interfaces & Adapters
interface OrderQueue {
    void put(Order order) throws InterruptedException;
    Order take() throws InterruptedException;
}

final class BlockingOrderQueue implements OrderQueue {
    private final java.util.concurrent.BlockingQueue<Order> q;
    BlockingOrderQueue(int capacity) { this.q = new java.util.concurrent.ArrayBlockingQueue<>(capacity); }
    public void put(Order o) throws InterruptedException { q.put(o); }
    public Order take() throws InterruptedException { return q.take(); }
}

// 3. Custom Bounded Buffer
class BoundedBuffer implements OrderQueue {
    private final ArrayDeque<Order> items = new ArrayDeque<>();
    private final int capacity;
    
    public BoundedBuffer(int capacity) { this.capacity = capacity; }

    @Override 
    public synchronized void put(Order order) throws InterruptedException {
        // Guarded block: wait while the buffer is full
        while (items.size() == capacity) {
            wait();
        }
        items.add(order);
        notifyAll(); // Wake up any waiting consumers
    }
    
    @Override 
    public synchronized Order take() throws InterruptedException {
        // Guarded block: wait while the buffer is empty
        while (items.isEmpty()) {
            wait();
        }
        Order order = items.poll();
        notifyAll(); // Wake up any waiting producers
        return order;
    }
}

// 4. Producer
class Producer implements Runnable {
    private final OrderQueue queue;
    private final int numOrders;
    private final int poisonPillsToSend;
    private final Random random = new Random();

    public Producer(OrderQueue queue, int numOrders, int poisonPillsToSend) {
        this.queue = queue;
        this.numOrders = numOrders;
        this.poisonPillsToSend = poisonPillsToSend;
    }

    @Override
    public void run() {
        try {
            for (int i = 1; i <= numOrders; i++) {
                int qty = random.nextInt(5) + 1; 
                Order order = new Order(i, "item-" + i, qty);
                Thread.sleep(random.nextInt(21)); // random 0-20 ms pause
                queue.put(order);
            }
            
            // Put one poison pill per consumer
            for (int i = 0; i < poisonPillsToSend; i++) {
                queue.put(new Order(-1, "poison", 0));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

// 5. Consumer
class Consumer implements Runnable {
    private final OrderQueue queue;
    private final AtomicInteger processedCount;
    private final Random random = new Random();

    public Consumer(OrderQueue queue, AtomicInteger processedCount) {
        this.queue = queue;
        this.processedCount = processedCount;
    }

    @Override
    public void run() {
        try {
            while (true) {
                Order order = queue.take();
                
                // Stop when it takes a poison pill
                if (order.id() == -1) {
                    System.out.println(Thread.currentThread().getName() + " received poison pill, stopping");
                    break;
                }
                
                Thread.sleep(random.nextInt(26) + 5); // Simulate processing: sleep 5-30 ms
                System.out.println(Thread.currentThread().getName() + " processed " + order);
                
                processedCount.incrementAndGet();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}