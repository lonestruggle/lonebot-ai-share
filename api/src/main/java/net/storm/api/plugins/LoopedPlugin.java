package net.storm.api.plugins;

/**
 * Storm-compat looped bot plugin.
 * {@link #loop()} returns delay in ms until next iteration.
 */
public abstract class LoopedPlugin {

    private volatile boolean running;

    public void startUp() {
        running = true;
    }

    public void shutDown() {
        running = false;
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * @return ms to wait before next loop call
     */
    public abstract int loop();
}
