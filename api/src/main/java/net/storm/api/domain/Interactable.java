package net.storm.api.domain;

/**
 * Entity that can be interacted with via the game menu / mouse click.
 * LoneBot uses {@code boolean} returns (Storm void variants are adapted).
 */
public interface Interactable {

    /**
     * Interact with a named action (e.g. {@code "Attack"}, {@code "Take"}).
     *
     * @return true if an interact was attempted successfully
     */
    boolean interact(String action);

    /** Interact with the first matching action name. */
    default boolean interact(String... actions) {
        if (actions == null) {
            return false;
        }
        for (String a : actions) {
            if (a != null && interact(a)) {
                return true;
            }
        }
        return false;
    }

    /** True if any of the given action names is present (when {@link #hasAction} exists). */
    default boolean hasAnyAction(String... actions) {
        if (actions == null) {
            return false;
        }
        for (String a : actions) {
            if (a != null && hasAction(a)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Optional action probe — default false; entities that know their actions override.
     */
    default boolean hasAction(String action) {
        return false;
    }
}
