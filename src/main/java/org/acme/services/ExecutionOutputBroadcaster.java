package org.acme.services;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@ApplicationScoped
public class ExecutionOutputBroadcaster {

    private final ConcurrentHashMap<Long, OutputSession> sessions = new ConcurrentHashMap<>();

    public void start(Long taskId) {
        sessions.compute(taskId, (k, existing) -> {
            if (existing != null) {
                for (MultiEmitter<? super String> emitter : existing.emitters) {
                    emitter.complete();
                }
            }
            return new OutputSession();
        });
    }

    public void publish(Long taskId, String line) {
        OutputSession session = sessions.get(taskId);
        if (session == null) return;
        session.buffer.add(line);
        for (MultiEmitter<? super String> emitter : session.emitters) {
            emitter.emit(line);
        }
    }

    public void complete(Long taskId) {
        OutputSession session = sessions.get(taskId);
        if (session == null) return;
        session.completed = true;
        for (MultiEmitter<? super String> emitter : session.emitters) {
            emitter.complete();
        }
    }

    public Multi<String> subscribe(Long taskId) {
        return Multi.createFrom().emitter(emitter -> {
            // Get or create session — supports subscribing before start()
            OutputSession session = sessions.computeIfAbsent(taskId, k -> new OutputSession());

            // Replay buffered lines
            for (String line : session.buffer) {
                emitter.emit(line);
            }
            if (session.completed) {
                emitter.complete();
                return;
            }
            // Register for live lines
            session.emitters.add(emitter);
            emitter.onTermination(() -> session.emitters.remove(emitter));
        });
    }

    static class OutputSession {
        final List<String> buffer = new CopyOnWriteArrayList<>();
        final List<MultiEmitter<? super String>> emitters = new CopyOnWriteArrayList<>();
        volatile boolean completed = false;
    }
}
