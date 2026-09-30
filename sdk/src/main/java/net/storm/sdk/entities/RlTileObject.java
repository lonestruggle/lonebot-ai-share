package net.storm.sdk.entities;

import net.runelite.api.Client;
import net.runelite.api.ObjectComposition;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.sdk.game.Static;
import net.storm.sdk.interact.MenuInteract;

final class RlTileObject implements ITileObject {

    private final TileObject object;

    RlTileObject(TileObject object) {
        this.object = object;
    }

    TileObject raw() {
        return object;
    }

    @Override
    public int getId() {
        Integer v = Static.callOnClientThread(object::getId, -1);
        return v != null ? v : -1;
    }

    @Override
    public String getName() {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return "";
            }
            ObjectComposition comp = MenuInteract.resolveObjectComposition(c, object.getId());
            return comp != null && comp.getName() != null ? comp.getName() : "";
        }, "");
    }

    @Override
    public WorldPoint getWorldLocation() {
        return Static.callOnClientThread(object::getWorldLocation, null);
    }

    @Override
    public boolean hasAction(String action) {
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            ObjectComposition comp = MenuInteract.resolveObjectComposition(c, object.getId());
            if (comp == null || comp.getActions() == null) {
                return false;
            }
            for (String a : comp.getActions()) {
                if (a != null && a.equalsIgnoreCase(action)) {
                    return true;
                }
            }
            return false;
        }, false));
    }

    @Override
    public boolean interact(String action) {
        // Alleen menu-invoke — geen canvas-LMB (Chop/Mine/Open/…).
        return MenuInteract.interactObject(object, action);
    }
}
