package org.acme.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class CancellationRegistryTest {

    private CancellationRegistry registry;

    @BeforeEach
    void setup() {
        registry = new CancellationRegistry();
    }

    @Test
    void cancelReturnsTrue_whenThreadIsRegisteredAndAlive() throws Exception {
        AtomicBoolean interrupted = new AtomicBoolean(false);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);

        Thread thread = Thread.startVirtualThread(() -> {
            started.countDown();
            try {
                Thread.sleep(30_000);
            } catch (InterruptedException e) {
                interrupted.set(true);
            } finally {
                done.countDown();
            }
        });

        assertTrue(started.await(5, TimeUnit.SECONDS));
        registry.register(1L, thread);

        boolean result = registry.cancel(1L);

        assertTrue(result);
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertTrue(interrupted.get());
    }

    @Test
    void cancelReturnsFalse_whenNoThreadIsRegistered() {
        boolean result = registry.cancel(999L);
        assertFalse(result);
    }

    @Test
    void cancelReturnsFalse_afterUnregister() throws Exception {
        CountDownLatch started = new CountDownLatch(1);

        Thread thread = Thread.startVirtualThread(() -> {
            started.countDown();
            try {
                Thread.sleep(30_000);
            } catch (InterruptedException e) {
                // expected
            }
        });

        assertTrue(started.await(5, TimeUnit.SECONDS));
        registry.register(1L, thread);
        registry.unregister(1L);

        boolean result = registry.cancel(1L);
        assertFalse(result);

        thread.interrupt(); // cleanup
    }

    @Test
    void registerWithSameTaskId_interruptsOldThread() throws Exception {
        AtomicBoolean oldInterrupted = new AtomicBoolean(false);
        CountDownLatch oldStarted = new CountDownLatch(1);
        CountDownLatch oldDone = new CountDownLatch(1);

        Thread oldThread = Thread.startVirtualThread(() -> {
            oldStarted.countDown();
            try {
                Thread.sleep(30_000);
            } catch (InterruptedException e) {
                oldInterrupted.set(true);
            } finally {
                oldDone.countDown();
            }
        });

        assertTrue(oldStarted.await(5, TimeUnit.SECONDS));
        registry.register(1L, oldThread);

        CountDownLatch newStarted = new CountDownLatch(1);
        Thread newThread = Thread.startVirtualThread(() -> {
            newStarted.countDown();
            try {
                Thread.sleep(30_000);
            } catch (InterruptedException e) {
                // expected
            }
        });

        assertTrue(newStarted.await(5, TimeUnit.SECONDS));
        registry.register(1L, newThread);

        assertTrue(oldDone.await(5, TimeUnit.SECONDS));
        assertTrue(oldInterrupted.get());

        newThread.interrupt(); // cleanup
    }

    @Test
    void cancelReturnsFalse_whenThreadAlreadyFinished() throws Exception {
        CountDownLatch done = new CountDownLatch(1);

        Thread thread = Thread.startVirtualThread(() -> {
            done.countDown();
            // finishes immediately
        });

        assertTrue(done.await(5, TimeUnit.SECONDS));
        thread.join(5000);
        registry.register(1L, thread);

        boolean result = registry.cancel(1L);
        assertFalse(result);
    }
}
