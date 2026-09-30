package net.storm.sdk.interact;

import net.runelite.api.MenuAction;
import net.runelite.api.Point;
import net.storm.api.interact.AutomatedMenu;
import net.storm.api.interact.Automation;
import net.storm.api.interact.InteractMethod;
import net.storm.api.interact.WidgetAction;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.interact.mouse.MouseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Storm {@link net.storm.api.interact.InteractManager} subset:
 * method override (INVOKE vs MOUSE_EVENTS) + FIFO automation queue.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/api/interact/InteractManager.html">Storm InteractManager</a>
 */
public final class InteractManager {

    private static final Logger log = LoggerFactory.getLogger(InteractManager.class);

    /** null = auto (prefer invoke for menus, mouse when clickPoint set). */
    private static volatile InteractMethod override;
    private static final ConcurrentLinkedQueue<Automation> QUEUE = new ConcurrentLinkedQueue<>();

    private InteractManager() {
    }

    public static void setInteractMethodOverride(InteractMethod method) {
        override = method;
        log.info("[InteractManager] override → {}", method != null ? method.name() : "AUTO");
        BotRuntime.logConsole("[Interact] method=" + (method != null ? method.name() : "AUTO"));
    }

    public static InteractMethod getInteractMethodOverride() {
        return override;
    }

    /** Storm {@code queue(Automation)}. */
    public static void queue(Automation automation) {
        if (automation != null) {
            QUEUE.offer(automation);
        }
    }

    /** @deprecated use {@link #queue(Automation)} */
    @Deprecated
    public static void enqueue(AutomatedMenu menu) {
        queue(menu);
    }

    public static void enqueue(Runnable task) {
        if (task != null) {
            queue(new RunnableAction(task));
        }
    }

    public static Queue<Automation> getQueue() {
        return QUEUE;
    }

    public static void clearQueue() {
        QUEUE.clear();
    }

    public static int queueSize() {
        return QUEUE.size();
    }

    /**
     * Dequeue and execute one entry. Call from {@link net.storm.sdk.loop.LoopHost} each tick.
     *
     * @return true if something was processed
     */
    public static boolean processNext() {
        Automation next = QUEUE.poll();
        if (next == null) {
            return false;
        }
        if (next instanceof RunnableAction) {
            try {
                ((RunnableAction) next).run();
            } catch (Throwable t) {
                log.warn("InteractManager Runnable failed: {}", t.toString());
            }
            return true;
        }
        if (next instanceof AutomatedMenu) {
            execute((AutomatedMenu) next);
            return true;
        }
        if (next instanceof WidgetAction) {
            executeWidget((WidgetAction) next);
            return true;
        }
        return false;
    }

    /** Drain up to {@code max} queued automations (one LoopHost tick). */
    public static int processUpTo(int max) {
        int n = 0;
        int limit = Math.max(1, max);
        while (n < limit && processNext()) {
            n++;
        }
        return n;
    }

    public static boolean execute(AutomatedMenu menu) {
        if (menu == null) {
            return false;
        }
        InteractMethod method = resolveMethod(menu.getInteractMethod());
        if (method == InteractMethod.MOUSE_EVENTS) {
            Point click = menu.getClickPoint();
            if (click != null) {
                return MouseManager.interactAt(click);
            }
            log.debug("MOUSE_EVENTS without clickPoint — falling back to invoke");
        }
        int itemId = menu.getItemId() > 0 ? menu.getItemId() : 0;
        return MenuInteract.invokeMenu(
                menu.getOption() != null ? menu.getOption() : "",
                menu.getTarget() != null ? menu.getTarget() : "",
                menu.getIdentifier(),
                menu.getOpcode(),
                menu.getParam0(),
                menu.getParam1(),
                itemId
        );
    }

    private static boolean executeWidget(WidgetAction action) {
        if (action == null) {
            return false;
        }
        InteractMethod method = resolveMethod(action.getInteractMethod());
        if (method == InteractMethod.MOUSE_EVENTS) {
            // Geen bounds hier — invoke CC_OP
            log.debug("WidgetAction MOUSE without bounds — invoke");
        }
        return MenuInteract.invokeMenu(
                action.getOption(),
                "",
                action.getIdentifier(),
                MenuAction.CC_OP.getId(),
                action.getParam0(),
                action.getPackedWidgetId(),
                0
        );
    }

    private static InteractMethod resolveMethod(InteractMethod perAction) {
        if (perAction != null) {
            return perAction;
        }
        if (override != null) {
            return override;
        }
        return InteractMethod.INVOKE;
    }

    /** Wrap Runnable as Automation. */
    public static final class RunnableAction implements Automation {
        private final Runnable runnable;

        public RunnableAction(Runnable runnable) {
            this.runnable = runnable;
        }

        public void run() {
            if (runnable != null) {
                runnable.run();
            }
        }
    }
}
