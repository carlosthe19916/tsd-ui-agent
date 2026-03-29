package org.acme.services;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class CancellationRegistry {

    private final ConcurrentHashMap<Long, Thread> runningThreads = new ConcurrentHashMap<>();

    public void register(Long taskId, Thread thread) {
        Thread existing = runningThreads.put(taskId, thread);
        if (existing != null && existing.isAlive()) {
            existing.interrupt();
        }
    }

    public void unregister(Long taskId) {
        runningThreads.remove(taskId);
    }

    public boolean cancel(Long taskId) {
        Thread thread = runningThreads.remove(taskId);
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
            return true;
        }
        return false;
    }
}
