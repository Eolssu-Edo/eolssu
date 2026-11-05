package com.ssafy.qa;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** A shell-owned bright-screen lease. No Activity, app input, or persistent settings. */
public final class BrightScreen {
    public static void main(String[] args) throws Exception {
        Class<?> binderType = Class.forName("android.os.IBinder");
        Object binder = Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, "power");
        Object power = Class.forName("android.os.IPowerManager$Stub")
                .getMethod("asInterface", binderType).invoke(null, binder);
        Class<?> service = Class.forName("android.os.IPowerManager");
        // Exact AOSP Android 12/16 signatures; never guess Binder transaction numbers.
        Method acquire;
        boolean callback;
        try {
            acquire = service.getMethod("acquireWakeLock", binderType, int.class,
                    String.class, String.class, Class.forName("android.os.WorkSource"),
                    String.class, int.class);
            callback = false;
        } catch (NoSuchMethodException legacyUnavailable) {
            acquire = service.getMethod("acquireWakeLock", binderType, int.class,
                    String.class, String.class, Class.forName("android.os.WorkSource"),
                    String.class, int.class, Class.forName("android.os.IWakeLockCallback"));
            callback = true;
        }
        Method release = service.getMethod("releaseWakeLock", binderType, int.class);
        Object token = Class.forName("android.os.Binder").getConstructor().newInstance();
        AtomicBoolean connected = new AtomicBoolean(true);
        AtomicLong heartbeat = new AtomicLong(System.nanoTime());
        // SCREEN_BRIGHT_WAKE_LOCK | ACQUIRE_CAUSES_WAKEUP, default display (0).
        if (callback) {
            acquire.invoke(power, token, 0x1000000a, "EolssuQA:connected", "com.android.shell", null, null, 0, null);
        } else {
            acquire.invoke(power, token, 0x1000000a, "EolssuQA:connected", "com.android.shell", null, null, 0);
        }
        try {
            Thread reader = new Thread(() -> {
                try {
                    BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
                    String line;
                    while ((line = input.readLine()) != null) {
                        if ("stop".equals(line)) break;
                        if ("ping".equals(line)) heartbeat.set(System.nanoTime());
                    }
                } catch (Exception ignored) {
                    // ADB loss ends the lease; it must not retain a screen lock.
                } finally {
                    connected.set(false);
                }
            }, "qa-screen-heartbeat");
            reader.setDaemon(true);
            reader.start();
            System.out.println("READY");
            System.out.flush();
            while (connected.get() && System.nanoTime() - heartbeat.get() < 15_000_000_000L) {
                Thread.sleep(250);
            }
        } finally {
            release.invoke(power, token, 0);
        }
    }
}
