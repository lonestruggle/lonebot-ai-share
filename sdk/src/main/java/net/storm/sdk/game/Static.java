package net.storm.sdk.game;

import net.runelite.api.Client;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Global RuneLite client handle used by Storm-compat static SDK helpers.
 */
public final class Static {

    private static volatile Client client;
    private static volatile Consumer<Runnable> clientThread;

    private Static() {
    }

    public static void setClient(Client c) {
        client = c;
    }

    public static Client getClient() {
        return client;
    }

    public static void setClientThread(Consumer<Runnable> invoker) {
        clientThread = invoker;
    }

    public static boolean isOnClientThread() {
        Client c = client;
        try {
            return c != null && c.isClientThread();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Run on RuneLite client thread when available (async queue). */
    public static void runOnClientThread(Runnable action) {
        if (action == null) {
            return;
        }
        if (isOnClientThread()) {
            action.run();
            return;
        }
        Consumer<Runnable> ct = clientThread;
        if (ct != null) {
            ct.accept(action);
        } else {
            action.run();
        }
    }

    /**
     * Blokkeer tot client-thread klaar is (coords / hull / inventory).
     * Timeout → null/false via supplier result.
     */
    public static void invokeAndWait(Runnable action) {
        invokeAndWait(action, 1500L);
    }

    public static void invokeAndWait(Runnable action, long timeoutMs) {
        if (action == null) {
            return;
        }
        if (isOnClientThread()) {
            action.run();
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        runOnClientThread(() -> {
            try {
                action.run();
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await(Math.max(50L, timeoutMs), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static <T> T callOnClientThread(Supplier<T> supplier) {
        return callOnClientThread(supplier, null);
    }

    public static <T> T callOnClientThread(Supplier<T> supplier, T fallback) {
        return callOnClientThread(supplier, fallback, 1500L);
    }

    public static <T> T callOnClientThread(Supplier<T> supplier, T fallback, long timeoutMs) {
        if (supplier == null) {
            return fallback;
        }
        if (isOnClientThread()) {
            try {
                return supplier.get();
            } catch (Throwable t) {
                return fallback;
            }
        }
        AtomicReference<T> ref = new AtomicReference<>(fallback);
        invokeAndWait(() -> {
            try {
                ref.set(supplier.get());
            } catch (Throwable ignored) {
                ref.set(fallback);
            }
        }, timeoutMs);
        return ref.get();
    }

    public static boolean isReady() {
        return client != null;
    }

    public static net.storm.api.movement.IMovement getMovement() {
        return net.storm.api.Static.getMovement();
    }

    public static net.storm.api.movement.IWalker getWalker() {
        return net.storm.api.Static.getWalker();
    }

    public static net.storm.api.widgets.IWidgets getWidgets() {
        return net.storm.sdk.widgets.Widgets.API;
    }

    public static net.storm.api.widgets.IDialog getDialog() {
        return net.storm.sdk.widgets.Dialog.API;
    }

    public static net.storm.api.widgets.ITabs getTabs() {
        return net.storm.sdk.widgets.Tabs.API;
    }

    public static net.storm.api.widgets.IFriends getFriends() {
        return net.storm.sdk.widgets.Friends.API;
    }

    public static net.storm.api.widgets.IPrayers getPrayers() {
        return net.storm.sdk.widgets.Prayer.API;
    }

    public static net.storm.api.widgets.IProduction getProduction() {
        return net.storm.sdk.widgets.Production.API;
    }

    public static net.storm.api.widgets.IMinigames getMinigames() {
        return net.storm.sdk.widgets.Minigames.API;
    }

    public static net.storm.api.widgets.IBankWornItems getBankWornItems() {
        return net.storm.sdk.widgets.BankEquipment.API;
    }
}
