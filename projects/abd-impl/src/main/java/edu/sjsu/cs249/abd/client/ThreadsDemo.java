package edu.sjsu.cs249.abd.client;

import java.util.concurrent.Executor;
    import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThreadsDemo {

    Executor executor = Executors.newFixedThreadPool(10);

    public static void main(String[] args) {
        ThreadsDemo demo = new ThreadsDemo();

        demo.executor.execute(() -> System.out.println("Hello, World!"));
    }
}
