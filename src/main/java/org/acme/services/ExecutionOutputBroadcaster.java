package org.acme.services;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@ApplicationScoped
public class ExecutionOutputBroadcaster {

    public enum Channel {
        GIT, WORKSPACE, TASK
    }

    private final ConcurrentHashMap<String, OutputSession> sessions = new ConcurrentHashMap<>();

    public void start(Channel channel, Long id) {
        String key = key(channel, id);
        sessions.compute(key, (k, existing) -> {
            if (existing != null) {
                for (MultiEmitter<? super String> emitter : existing.emitters) {
                    emitter.complete();
                }
            }
            return new OutputSession();
        });
    }

    public void publish(Channel channel, Long id, String line) {
        OutputSession session = sessions.get(key(channel, id));
        if (session == null) return;
        session.buffer.add(line);
        for (MultiEmitter<? super String> emitter : session.emitters) {
            emitter.emit(line);
        }
    }

    public void complete(Channel channel, Long id) {
        OutputSession session = sessions.get(key(channel, id));
        if (session == null) return;
        session.completed = true;
        for (MultiEmitter<? super String> emitter : session.emitters) {
            emitter.complete();
        }
    }

    public void cancel(Channel channel, Long id) {
        String key = key(channel, id);
        OutputSession session = sessions.get(key);
        if (session == null) return;
        session.buffer.add("[Cancelled by user]");
        session.completed = true;
        for (MultiEmitter<? super String> emitter : session.emitters) {
            emitter.emit("[Cancelled by user]");
            emitter.complete();
        }
    }

    public Multi<String> subscribe(Channel channel, Long id) {
        String key = key(channel, id);
        return Multi.createFrom().emitter(emitter -> {
            // Get or create session — supports subscribing before start()
            OutputSession session = sessions.computeIfAbsent(key, k -> new OutputSession());

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

    private static String key(Channel channel, Long id) {
        return channel.name() + ":" + id;
    }

    static class OutputSession {
        final List<String> buffer = new CopyOnWriteArrayList<>();
        final List<MultiEmitter<? super String>> emitters = new CopyOnWriteArrayList<>();
        volatile boolean completed = false;
    }
}
