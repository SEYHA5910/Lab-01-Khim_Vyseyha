package edu.itc.se.lab01.task1;

/*
 * EXPLANATION: start() vs run()
 * Calling .start() allocates a new, independent thread of execution so tasks run concurrently. 
 * Calling .run() directly does not create a new thread; it executes the code synchronously 
 * on the current main thread, blocking all other execution until it finishes.
 */
public class Main {
    public static void main(String[] args) throws InterruptedException {
        boolean sequential = args.length > 0 && args[0].equals("--sequential");

        // 1. Subclass technique
        Thread apollo = new Countdown("apollo");
        // 2. Runnable technique
        Thread gemini = new Thread(new CountdownTask(), "gemini");
        // 3. Lambda technique
        Thread mercury = new Thread(() -> new CountdownTask().run(), "mercury");
        // 4. Virtual Thread builder
        Thread artemis = Thread.ofVirtual().name("artemis").unstarted(new CountdownTask());

        if (sequential) {
            apollo.run();
            gemini.run();
            mercury.run();
            artemis.run();
        } else {
            apollo.start();
            gemini.start();
            mercury.start();
            artemis.start();
        }

        System.out.println("Mission control: all launched");

        if (!sequential) {
            apollo.join();
            gemini.join();
            mercury.join();
            artemis.join();
        }

        System.out.println("Mission control: all landed");

        System.out.printf("%-8s virtual=%-5b state=%s%n", apollo.getName(), apollo.isVirtual(), apollo.getState());
        System.out.printf("%-8s virtual=%-5b state=%s%n", gemini.getName(), gemini.isVirtual(), gemini.getState());
        System.out.printf("%-8s virtual=%-5b state=%s%n", mercury.getName(), mercury.isVirtual(), mercury.getState());
        System.out.printf("%-8s virtual=%-5b state=%s%n", artemis.getName(), artemis.isVirtual(), artemis.getState());
    }
}

class Countdown extends Thread {
    public Countdown(String name) {
        super(name);
    }

    @Override
    public void run() {
        try {
            for (int i = 3; i > 0; i--) {
                System.out.println(getName() + ": " + i);
                Thread.sleep(200);
            }
            System.out.println(getName() + ": liftoff!");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

// Notice there is no "public" keyword before "class" here
class CountdownTask implements Runnable {
    @Override
    public void run() {
        try {
            for (int i = 3; i > 0; i--) {
                System.out.println(Thread.currentThread().getName() + ": " + i);
                Thread.sleep(200);
            }
            System.out.println(Thread.currentThread().getName() + ": liftoff!");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}