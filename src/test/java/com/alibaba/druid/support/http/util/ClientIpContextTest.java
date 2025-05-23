package com.alibaba.druid.support.http.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class ClientIpContextTest {

    // Helper method to clear the static map for test isolation, if needed beyond clearCurrentClientIp()
    private void fullyClearActiveThreadsMap() throws Exception {
        Field field = ClientIpContext.class.getDeclaredField("active127_0_0_1_Threads");
        field.setAccessible(true);
        ConcurrentHashMap<String, Boolean> map = (ConcurrentHashMap<String, Boolean>) field.get(null);
        map.clear();
    }

    @BeforeEach
    @AfterEach
    void tearDown() throws Exception {
        // Ensure the context is clean for each test, especially the static map.
        // ClientIpContext.clearCurrentClientIp() relies on the ThreadLocal value being set.
        // A more direct clear might be needed if tests manipulate threads or end without clearing.
        ClientIpContext.currentClientIp.remove(); // Clear thread-local
        fullyClearActiveThreadsMap(); // Clear the static map tracking 127.0.0.1 threads
    }

    @Test
    void testSetAndGetClientIp_non127() {
        String testIp = "192.168.1.10";
        ClientIpContext.setCurrentClientIp(testIp);
        assertEquals(testIp, ClientIpContext.getCurrentClientIp(), "IP should be set correctly.");
        assertFalse(ClientIpContext.hasActive127_0_0_1_Request(), "Should not have active 127.0.0.1 request for non-127 IP.");
        
        ClientIpContext.clearCurrentClientIp();
        assertNull(ClientIpContext.getCurrentClientIp(), "IP should be cleared.");
        assertFalse(ClientIpContext.hasActive127_0_0_1_Request(), "Still should not have active 127.0.0.1 request after clear.");
    }

    @Test
    void testSetAndGetClientIp_is127() {
        String testIp = "127.0.0.1";
        ClientIpContext.setCurrentClientIp(testIp);
        assertEquals(testIp, ClientIpContext.getCurrentClientIp(), "IP should be set correctly.");
        assertTrue(ClientIpContext.hasActive127_0_0_1_Request(), "Should have active 127.0.0.1 request for 127.0.0.1 IP.");

        ClientIpContext.clearCurrentClientIp();
        assertNull(ClientIpContext.getCurrentClientIp(), "IP should be cleared.");
        assertFalse(ClientIpContext.hasActive127_0_0_1_Request(), "Should not have active 127.0.0.1 request after clear.");
    }

    @Test
    void testClearClientIp_nonMatchingIp() {
        ClientIpContext.setCurrentClientIp("192.168.1.10");
        ClientIpContext.clearCurrentClientIp();
        assertFalse(ClientIpContext.hasActive127_0_0_1_Request(), "hasActive... should remain false after clearing non-127 IP.");
    }

    @Test
    void testClearClientIp_matchingIp() {
        ClientIpContext.setCurrentClientIp("127.0.0.1");
        ClientIpContext.clearCurrentClientIp();
        assertFalse(ClientIpContext.hasActive127_0_0_1_Request(), "hasActive... should be false after clearing 127 IP.");
    }

    @Test
    void testHasActive_multiple127Requests_simulated() throws Exception {
        // Simulate Thread 1 (current thread with name "Thread-1" for map key)
        Thread originalThread = Thread.currentThread();
        String originalName = originalThread.getName();
        originalThread.setName("Thread-1-Simulated");

        ClientIpContext.setCurrentClientIp("127.0.0.1");
        assertTrue(ClientIpContext.hasActive127_0_0_1_Request(), "Active after Thread-1 sets 127.0.0.1");

        // Simulate Thread 2 (current thread, but change name for map key)
        originalThread.setName("Thread-2-Simulated");
        // Note: setCurrentClientIp uses the *new* thread name for its key
        // but clearCurrentClientIp for Thread-1 would need "Thread-1-Simulated".
        // This sequential simulation of different threads in one test thread is tricky
        // because ThreadLocal is tied to the *actual* current thread.
        // The map key logic is what we are testing for multiple entries.
        
        // To properly test the map part, let's directly manipulate it (as if another thread did)
        Field field = ClientIpContext.class.getDeclaredField("active127_0_0_1_Threads");
        field.setAccessible(true);
        ConcurrentHashMap<String, Boolean> map = (ConcurrentHashMap<String, Boolean>) field.get(null);
        map.put("Another-Thread-Key", true); // Simulate another thread added itself

        assertTrue(ClientIpContext.hasActive127_0_0_1_Request(), "Active due to current thread and simulated other thread.");

        // Current thread (acting as Thread-1) clears its IP
        originalThread.setName("Thread-1-Simulated"); // ensure correct key for clear
        ClientIpContext.clearCurrentClientIp(); // Removes "Thread-1-Simulated" from map
        assertTrue(ClientIpContext.hasActive127_0_0_1_Request(), "Still active due to simulated other thread.");
        
        map.remove("Another-Thread-Key"); // Simulate the other thread ending
        assertFalse(ClientIpContext.hasActive127_0_0_1_Request(), "Not active after all 127.0.0.1 sources are cleared.");

        originalThread.setName(originalName); // Restore original thread name
    }
     @Test
    void testContextIsolationAcrossThreads() throws InterruptedException {
        final String ip1 = "127.0.0.1";
        final String ip2 = "192.168.1.100";
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(2);

        Thread thread1 = new Thread(() -> {
            ClientIpContext.setCurrentClientIp(ip1);
            assertEquals(ip1, ClientIpContext.getCurrentClientIp());
            assertTrue(ClientIpContext.hasActive127_0_0_1_Request()); // Map has this thread
            try {
                Thread.sleep(100); // Give thread2 a chance to run
            } catch (InterruptedException e) {}
            assertTrue(ClientIpContext.hasActive127_0_0_1_Request()); // Still true
            ClientIpContext.clearCurrentClientIp();
            assertFalse(ClientIpContext.hasActive127_0_0_1_Request()); // Map *might* be empty if thread2 is done
            latch.countDown();
        }, "Thread-IP1");

        Thread thread2 = new Thread(() -> {
            ClientIpContext.setCurrentClientIp(ip2);
            assertEquals(ip2, ClientIpContext.getCurrentClientIp());
            // hasActive127_0_0_1_Request depends on thread1's state if it ran first
            try {
                Thread.sleep(50); // Let thread1 set its IP
            } catch (InterruptedException e) {}
             // If thread1 has set "127.0.0.1", this will be true.
            // This assertion is more about thread1's effect than thread2's isolation on the shared map.
            // boolean thread1Active = ClientIpContext.hasActive127_0_0_1_Request();
            ClientIpContext.clearCurrentClientIp(); // Clears ip2, doesn't affect map for 127.0.0.1
            // If thread1 is still active and set 127.0.0.1, this remains true.
            // assertTrue(ClientIpContext.hasActive127_0_0_1_Request() == thread1Active); 
            latch.countDown();
        }, "Thread-IP2");

        thread1.start();
        thread2.start();
        latch.await();
        
        // After both threads complete and clear their contexts, the map should be empty.
        assertFalse(ClientIpContext.hasActive127_0_0_1_Request(), "Map should be empty after threads complete.");
    }
}
