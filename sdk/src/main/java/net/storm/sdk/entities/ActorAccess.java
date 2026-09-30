package net.storm.sdk.entities;

import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.actors.IActor;
import net.storm.sdk.game.Static;

import java.awt.Shape;
import java.util.Collection;

/**
 * Live Actor reads on the client thread + wrap interacting NPC/player.
 */
final class ActorAccess {

    private ActorAccess() {
    }

    static IActor wrap(Actor actor) {
        if (actor == null) {
            return null;
        }
        if (actor instanceof NPC) {
            return new RlNpc((NPC) actor);
        }
        if (actor instanceof Player) {
            return new RlPlayer((Player) actor);
        }
        return null;
    }

    static String name(Actor a) {
        return Static.callOnClientThread(() -> a != null ? a.getName() : null, "");
    }

    static WorldPoint world(Actor a) {
        return Static.callOnClientThread(() -> a != null ? a.getWorldLocation() : null, null);
    }

    static int animation(Actor a) {
        Integer v = Static.callOnClientThread(() -> a != null ? a.getAnimation() : -1, -1);
        return v != null ? v : -1;
    }

    /**
     * Echte beweging: destination of walk/run-pose — niet {@code pose != idle}
     * (stance/idle-varianten gaven vals-positief "moving" → FM nooit tinderbox).
     */
    static boolean moving(Actor a) {
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            if (a == null) {
                return false;
            }
            try {
                Client c = Static.getClient();
                if (c != null && c.getLocalPlayer() == a) {
                    LocalPoint dest = c.getLocalDestinationLocation();
                    if (dest != null) {
                        return true;
                    }
                }
            } catch (Throwable ignored) {
            }
            try {
                int pose = a.getPoseAnimation();
                int walk = a.getWalkAnimation();
                int run = a.getRunAnimation();
                if (walk != -1 && pose == walk) {
                    return true;
                }
                if (run != -1 && pose == run) {
                    return true;
                }
                if (pose == a.getWalkRotateLeft() || pose == a.getWalkRotateRight()
                        || pose == a.getWalkRotate180()) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
            return false;
        }, false));
    }

    static int healthRatio(Actor a) {
        Integer v = Static.callOnClientThread(() -> a != null ? a.getHealthRatio() : -1, -1);
        return v != null ? v : -1;
    }

    static int healthScale(Actor a) {
        Integer v = Static.callOnClientThread(() -> a != null ? a.getHealthScale() : -1, -1);
        return v != null ? v : -1;
    }

    static IActor interacting(Actor a) {
        return Static.callOnClientThread(() -> a != null ? wrap(a.getInteracting()) : null, null);
    }

    static boolean isInteracting(Actor a) {
        return Boolean.TRUE.equals(Static.callOnClientThread(
                () -> a != null && a.getInteracting() != null, false));
    }

    static int spotAnimCount(Actor a) {
        Integer v = Static.callOnClientThread(() -> {
            if (a == null) {
                return 0;
            }
            try {
                Object spots = a.getClass().getMethod("getSpotAnims").invoke(a);
                if (spots instanceof Collection) {
                    return ((Collection<?>) spots).size();
                }
                if (spots instanceof Iterable) {
                    int n = 0;
                    for (Object ignored : (Iterable<?>) spots) {
                        n++;
                    }
                    return n;
                }
            } catch (Throwable ignored) {
            }
            try {
                int g = a.getGraphic();
                return g != -1 ? 1 : 0;
            } catch (Throwable t) {
                return 0;
            }
        }, 0);
        return v != null ? v : 0;
    }

    static int combatLevel(Actor a) {
        Integer v = Static.callOnClientThread(() -> {
            if (a instanceof NPC) {
                return ((NPC) a).getCombatLevel();
            }
            if (a instanceof Player) {
                return ((Player) a).getCombatLevel();
            }
            try {
                return (Integer) a.getClass().getMethod("getCombatLevel").invoke(a);
            } catch (Throwable t) {
                return 0;
            }
        }, 0);
        return v != null ? v : 0;
    }

    static String overheadText(Actor a) {
        return Static.callOnClientThread(() -> a != null ? a.getOverheadText() : null, null);
    }

    static LocalPoint local(Actor a) {
        return Static.callOnClientThread(() -> a != null ? a.getLocalLocation() : null, null);
    }

    static WorldArea worldArea(Actor a) {
        return Static.callOnClientThread(() -> a != null ? a.getWorldArea() : null, null);
    }

    static int orientation(Actor a) {
        Integer v = Static.callOnClientThread(() -> a != null ? a.getOrientation() : 0, 0);
        return v != null ? v : 0;
    }

    static int poseAnimation(Actor a) {
        Integer v = Static.callOnClientThread(() -> a != null ? a.getPoseAnimation() : -1, -1);
        return v != null ? v : -1;
    }

    static int idlePose(Actor a) {
        Integer v = Static.callOnClientThread(() -> a != null ? a.getIdlePoseAnimation() : -1, -1);
        return v != null ? v : -1;
    }

    static int graphic(Actor a) {
        Integer v = Static.callOnClientThread(() -> a != null ? a.getGraphic() : -1, -1);
        return v != null ? v : -1;
    }

    static Shape convexHull(Actor a) {
        return Static.callOnClientThread(() -> a != null ? a.getConvexHull() : null, null);
    }

    static Point minimap(Actor a) {
        return Static.callOnClientThread(() -> a != null ? a.getMinimapLocation() : null, null);
    }

    static int logicalHeight(Actor a) {
        Integer v = Static.callOnClientThread(() -> {
            if (a == null) {
                return 0;
            }
            try {
                return a.getLogicalHeight();
            } catch (Throwable t) {
                return 0;
            }
        }, 0);
        return v != null ? v : 0;
    }

    static Object worldView(Actor a) {
        return Static.callOnClientThread(() -> {
            if (a == null) {
                return null;
            }
            try {
                return a.getClass().getMethod("getWorldView").invoke(a);
            } catch (Throwable t) {
                Client c = Static.getClient();
                if (c == null) {
                    return null;
                }
                try {
                    return c.getClass().getMethod("getTopLevelWorldView").invoke(c);
                } catch (Throwable ignored) {
                    return null;
                }
            }
        }, null);
    }
}
