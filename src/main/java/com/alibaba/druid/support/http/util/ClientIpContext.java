package com.alibaba.druid.support.http.util;

import java.util.concurrent.ConcurrentHashMap;

public class ClientIpContext {
    private static final ThreadLocal<String> currentClientIp = new ThreadLocal<>();
    // Using ConcurrentHashMap to store active thread names (or IDs) for requests from 127.0.0.1
    private static final ConcurrentHashMap<String, Boolean> active127_0_0_1_Threads = new ConcurrentHashMap<>();

    public static void setCurrentClientIp(String ip) {
        currentClientIp.set(ip);
        if ("127.0.0.1".equals(ip)) {
            // Using thread name as a simple identifier. Consider Thread.getId() if preferred.
            active127_0_0_1_Threads.put(Thread.currentThread().getName(), true);
        }
    }

    public static String getCurrentClientIp() {
        return currentClientIp.get();
    }

    public static void clearCurrentClientIp() {
        String ip = currentClientIp.get();
        if ("127.0.0.1".equals(ip)) {
            active127_0_0_1_Threads.remove(Thread.currentThread().getName());
        }
        currentClientIp.remove();
    }

    public static boolean hasActive127_0_0_1_Request() {
        return !active127_0_0_1_Threads.isEmpty();
    }
}
