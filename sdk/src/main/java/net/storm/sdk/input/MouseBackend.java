package net.storm.sdk.input;

/**
 * Welke manier we gebruiken om muis/klik naar het spel te sturen.
 * Alleen focus-safe backends: geen Robot / requestFocus (venster blijft inactief).
 */
public enum MouseBackend {
    /** Huidige aanpak: MouseEvent → canvas.dispatchEvent (vanaf bot-thread). */
    CANVAS_DISPATCH("1. Canvas dispatch"),

    /** Zelfde events, maar op Swing EDT (invokeAndWait). */
    CANVAS_EDT("2. Canvas op EDT"),

    /**
     * EDT + MouseEvent-constructor met screenX/screenY
     * (StackOverflow / Ganom-stijl).
     */
    CANVAS_EDT_SCREEN("3. Canvas EDT + screen coords"),

    /**
     * @deprecated Steelt OS-focus — geblokkeerd door {@link MouseSettings#neverStealFocus()}.
     */
    @Deprecated
    AWT_ROBOT("4. AWT Robot (GEBLOKKEERD)"),

    /**
     * @deprecated Steelt OS-focus — geblokkeerd.
     */
    @Deprecated
    ROBOT_FOCUS("5. Focus+Robot (GEBLOKKEERD)"),

    /** Geen muis — alleen invokeMenuAction (bewijs A/B pad). */
    MENU_ONLY("6. Alleen menu-invoke");

    private final String label;

    MouseBackend(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Backends die het venster actief kunnen maken (Robot / focus). */
    public boolean stealsFocus() {
        return this == AWT_ROBOT || this == ROBOT_FOCUS;
    }

    /** Backends die veilig zijn bij neverStealFocus. */
    public boolean isFocusSafe() {
        return !stealsFocus();
    }

    @Override
    public String toString() {
        return label;
    }
}
