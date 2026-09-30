package net.storm.sdk.script.blocking_events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry for script blocking events (randoms, login, …).
 *
 * @stub In-memory register/clear only — events are not dispatched.
 */
public final class BlockingEventManager {

    private static final List<Object> EVENTS = new CopyOnWriteArrayList<>();

    private BlockingEventManager() {
    }

    /** @stub Stores the handler object; no type checks / dispatch. */
    public static void register(Object event) {
        if (event != null) {
            EVENTS.add(event);
        }
    }

    public static void clear() {
        EVENTS.clear();
    }

    /** Snapshot of registered stubs. */
    public static List<Object> getRegistered() {
        return Collections.unmodifiableList(new ArrayList<>(EVENTS));
    }
}
