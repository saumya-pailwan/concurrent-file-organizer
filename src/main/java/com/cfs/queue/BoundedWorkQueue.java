package com.cfs.queue;

import com.cfs.traversal.FileTask;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

// Bounded blocking queue: produce() blocks when full, consume() blocks when empty.
public class BoundedWorkQueue {

    private final BlockingQueue<FileTask> queue;

    public BoundedWorkQueue(int capacity) {
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    /** Blocks if the queue is full (backpressure on the producer). */
    public void produce(FileTask task) {
        try {
            queue.put(task);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Producer interrupted", e);
        }
    }

    /** Blocks if the queue is empty (worker waits for next task). */
    public FileTask consume() {
        try {
            return queue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Consumer interrupted", e);
        }
    }

    public int size() {
        return queue.size();
    }
}
