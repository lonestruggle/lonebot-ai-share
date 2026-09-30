package net.runelite.client.plugins.lonebot.dev;

import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.GraphicsObject;
import net.runelite.api.GroundObject;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Projectile;
import net.runelite.api.Tile;
import net.runelite.api.WallObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.lonebot.LoneBotConfig;
import net.runelite.client.plugins.lonebot.ScriptOverlayGate;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.storm.api.movement.TilePath;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.movement.MovementHelper;
import net.storm.sdk.movement.Reachable;
import net.storm.sdk.movement.WorldWalker;

import javax.inject.Inject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Shape;
import java.util.List;

/**
 * Storm Developer Tools overlays: reachable, collision, highlights, path, projectiles.
 */
public class DevToolsOverlay extends Overlay {

    private static final int NEAR = 16;

    private final Client client;
    private final LoneBotConfig config;

    private List<WorldPoint> reachableCache;
    private int reachableTick = -1;

    @Inject
    public DevToolsOverlay(Client client, LoneBotConfig config) {
        this.client = client;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPriority(PRIORITY_LOW);
    }

    @Override
    public Dimension render(Graphics2D g) {
        if (config == null || client == null || client.getGameState() != GameState.LOGGED_IN) {
            return null;
        }
        Player me = client.getLocalPlayer();
        if (me == null || me.getWorldLocation() == null) {
            return null;
        }
        WorldPoint myWp = me.getWorldLocation();

        TilePath walkPath = activeWalkPath(myWp);
        WorldPoint walkDest = walkDestination(walkPath);
        boolean showPath = ScriptOverlayGate.walkPath(config)
                && (walkPath != null || walkDest != null || config.devDebugOverlay());
        if (showPath) {
            drawPath(g, walkPath);
            if (walkDest != null) {
                fillTile(g, walkDest, new Color(255, 200, 40, 90), new Color(255, 200, 40, 220));
                drawMinimapDot(g, walkDest, new Color(255, 200, 40, 220), 3);
            }
        }
        if (config.devReachableTiles()) {
            drawReachable(g, myWp);
        }
        if (config.devCollisionData()) {
            drawCollision(g, myWp);
        }
        if (config.devHighlightNpcs() || config.devExtNpcGraphics()) {
            drawNpcs(g, myWp);
        }
        if (config.devHighlightGameObjects() || config.devHighlightWallObjects()
                || config.devHighlightGroundObjects() || config.devHighlightDecorativeObjects()) {
            drawObjects(g, myWp);
        }
        if (config.devProjectiles()) {
            drawProjectiles(g);
        }
        return null;
    }

    private static TilePath activeWalkPath(WorldPoint myWp) {
        TilePath path = MovementHelper.getActivePath();
        if (path == null || path.isEmpty()) {
            path = BotRuntime.debugPath;
        }
        if (path == null || path.isEmpty()) {
            return null;
        }
        if (myWp != null) {
            TilePath rem = path.getRemainingPath(myWp);
            if (rem != null && !rem.isEmpty()) {
                return rem;
            }
        }
        return path;
    }

    private static WorldPoint walkDestination(TilePath path) {
        WorldPoint dest = WorldWalker.destination();
        if (dest != null) {
            return dest;
        }
        dest = MovementHelper.getActiveDestination();
        if (dest != null) {
            return dest;
        }
        dest = BotRuntime.debugTarget;
        if (dest != null) {
            return dest;
        }
        return path != null ? path.getDestination() : null;
    }

    private void drawPath(Graphics2D g, TilePath path) {
        if (path == null || path.isEmpty()) {
            return;
        }
        WorldPoint prev = null;
        int n = 0;
        for (WorldPoint p : path) {
            if (p == null) {
                continue;
            }
            n++;
            if (n > 280) {
                break;
            }
            Color fill = n == 1
                    ? new Color(80, 220, 255, 70)
                    : new Color(80, 220, 120, 45);
            Color stroke = n == 1
                    ? new Color(80, 220, 255, 220)
                    : new Color(80, 220, 120, 170);
            fillTile(g, p, fill, stroke);
            drawMinimapDot(g, p, stroke, n == 1 ? 3 : 2);
            if (prev != null) {
                drawLine(g, prev, p, new Color(80, 220, 120, 150));
            }
            prev = p;
        }
    }

    private void drawMinimapDot(Graphics2D g, WorldPoint wp, Color color, int r) {
        LocalPoint lp = LocalPoint.fromWorld(client, wp);
        if (lp == null) {
            return;
        }
        Point mm = Perspective.localToMinimap(client, lp);
        if (mm == null) {
            return;
        }
        g.setColor(color);
        g.fillOval(mm.getX() - r, mm.getY() - r, r * 2, r * 2);
    }

    private void drawReachable(Graphics2D g, WorldPoint myWp) {
        int tick = client.getTickCount();
        if (reachableCache == null || tick != reachableTick) {
            reachableCache = Reachable.getVisitedTiles(myWp);
            reachableTick = tick;
        }
        if (reachableCache == null) {
            return;
        }
        int drawn = 0;
        for (WorldPoint p : reachableCache) {
            if (p == null || p.distanceTo(myWp) > NEAR) {
                continue;
            }
            fillTile(g, p, new Color(40, 180, 255, 35), new Color(40, 180, 255, 90));
            if (++drawn > 400) {
                break;
            }
        }
    }

    private void drawCollision(Graphics2D g, WorldPoint myWp) {
        CollisionData[] maps = client.getCollisionMaps();
        int plane = client.getPlane();
        if (maps == null || plane < 0 || plane >= maps.length || maps[plane] == null) {
            return;
        }
        int[][] flags = maps[plane].getFlags();
        Tile[][][] tiles = client.getScene() != null ? client.getScene().getTiles() : null;
        if (flags == null || tiles == null || tiles[plane] == null) {
            return;
        }
        for (int sx = 0; sx < flags.length; sx++) {
            int[] row = flags[sx];
            if (row == null) {
                continue;
            }
            for (int sy = 0; sy < row.length; sy++) {
                if ((row[sy] & CollisionDataFlag.BLOCK_MOVEMENT_FULL) == 0) {
                    continue;
                }
                Tile t = tiles[plane][sx][sy];
                if (t == null || t.getWorldLocation() == null) {
                    continue;
                }
                if (t.getWorldLocation().distanceTo(myWp) > NEAR) {
                    continue;
                }
                fillTile(g, t.getWorldLocation(), new Color(220, 40, 40, 50), new Color(220, 40, 40, 140));
            }
        }
    }

    private void drawNpcs(Graphics2D g, WorldPoint myWp) {
        for (NPC n : client.getNpcs()) {
            if (n == null || n.getWorldLocation() == null) {
                continue;
            }
            if (n.getWorldLocation().distanceTo(myWp) > NEAR) {
                continue;
            }
            if (config.devHighlightNpcs()) {
                Shape hull = n.getConvexHull();
                if (hull != null) {
                    g.setColor(new Color(255, 160, 40, 40));
                    g.fill(hull);
                    g.setColor(new Color(255, 160, 40, 200));
                    g.setStroke(new BasicStroke(2f));
                    g.draw(hull);
                }
            }
            if (config.devExtNpcGraphics() && npcHasGraphics(n)) {
                fillTile(g, n.getWorldLocation(),
                        new Color(180, 80, 255, 50), new Color(180, 80, 255, 200));
            }
        }
        if (config.devExtNpcGraphics()) {
            for (GraphicsObject go : client.getGraphicsObjects()) {
                if (go == null || go.getLocation() == null) {
                    continue;
                }
                WorldPoint wp = WorldPoint.fromLocal(client, go.getLocation());
                if (wp == null || wp.distanceTo(myWp) > NEAR) {
                    continue;
                }
                fillTile(g, wp, new Color(200, 80, 255, 40), new Color(200, 80, 255, 180));
            }
        }
    }

    private void drawObjects(Graphics2D g, WorldPoint myWp) {
        Tile[][][] tiles = client.getScene() != null ? client.getScene().getTiles() : null;
        if (tiles == null) {
            return;
        }
        int plane = client.getPlane();
        Tile[][] planeTiles = tiles[plane];
        if (planeTiles == null) {
            return;
        }
        for (Tile[] row : planeTiles) {
            if (row == null) {
                continue;
            }
            for (Tile tile : row) {
                if (tile == null || tile.getWorldLocation() == null) {
                    continue;
                }
                if (tile.getWorldLocation().distanceTo(myWp) > NEAR) {
                    continue;
                }
                if (config.devHighlightGameObjects() && tile.getGameObjects() != null) {
                    for (GameObject go : tile.getGameObjects()) {
                        outline(g, go != null ? go.getConvexHull() : null, new Color(255, 200, 60, 180));
                    }
                }
                if (config.devHighlightWallObjects()) {
                    WallObject w = tile.getWallObject();
                    outline(g, w != null ? w.getConvexHull() : null, new Color(255, 80, 80, 200));
                }
                if (config.devHighlightGroundObjects()) {
                    GroundObject gr = tile.getGroundObject();
                    outline(g, gr != null ? gr.getConvexHull() : null, new Color(160, 120, 60, 200));
                }
                if (config.devHighlightDecorativeObjects()) {
                    DecorativeObject d = tile.getDecorativeObject();
                    outline(g, d != null ? d.getConvexHull() : null, new Color(180, 100, 255, 200));
                }
            }
        }
    }

    private void drawProjectiles(Graphics2D g) {
        for (Projectile p : client.getProjectiles()) {
            if (p == null) {
                continue;
            }
            try {
                LocalPoint lp = new LocalPoint((int) p.getX(), (int) p.getY());
                Point a = Perspective.localToCanvas(client, lp, p.getFloor(), (int) p.getZ());
                if (a == null) {
                    continue;
                }
                g.setColor(new Color(255, 255, 80, 220));
                g.setStroke(new BasicStroke(2f));
                g.drawOval(a.getX() - 4, a.getY() - 4, 8, 8);
            } catch (Throwable ignored) {
            }
        }
    }

    private static boolean npcHasGraphics(NPC n) {
        try {
            if (n.getGraphic() > 0) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            return n.getSpotAnims() != null && n.getSpotAnims().iterator().hasNext();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void outline(Graphics2D g, Shape hull, Color color) {
        if (hull == null) {
            return;
        }
        Color fill = new Color(color.getRed(), color.getGreen(), color.getBlue(), 35);
        g.setColor(fill);
        g.fill(hull);
        g.setColor(color);
        g.setStroke(new BasicStroke(1.5f));
        g.draw(hull);
    }

    private void fillTile(Graphics2D g, WorldPoint wp, Color fill, Color stroke) {
        LocalPoint lp = LocalPoint.fromWorld(client, wp);
        if (lp == null) {
            return;
        }
        Polygon poly = Perspective.getCanvasTilePoly(client, lp);
        if (poly == null) {
            return;
        }
        g.setColor(fill);
        g.fillPolygon(poly);
        g.setColor(stroke);
        g.setStroke(new BasicStroke(1.2f));
        g.drawPolygon(poly);
    }

    private void drawLine(Graphics2D g, WorldPoint a, WorldPoint b, Color color) {
        LocalPoint la = LocalPoint.fromWorld(client, a);
        LocalPoint lb = LocalPoint.fromWorld(client, b);
        if (la == null || lb == null) {
            return;
        }
        Point pa = Perspective.localToCanvas(client, la, a.getPlane());
        Point pb = Perspective.localToCanvas(client, lb, b.getPlane());
        if (pa == null || pb == null) {
            return;
        }
        g.setColor(color);
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(pa.getX(), pa.getY(), pb.getX(), pb.getY());
    }
}
