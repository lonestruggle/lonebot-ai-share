package net.storm.sdk.items.info;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.storm.sdk.game.Static;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Lookup for {@link ItemInfo} from the bundled item-stats dump (RuneLite cache format).
 * Noted / bank-placeholder IDs map to the base item.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/sdk/items/info/ItemInfos.html">Storm ItemInfos</a>
 */
public class ItemInfos {

    private static final Logger log = LoggerFactory.getLogger(ItemInfos.class);
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, RawItem>>() {
    }.getType();

    private static volatile Map<Integer, RawItem> BY_ID;

    public ItemInfos() {
    }

    /**
     * @return stats for {@code itemId}, or {@code null} if the dump has no row
     */
    public static ItemInfo lookup(int itemId) {
        if (itemId < 0) {
            return null;
        }
        Map<Integer, RawItem> map = load();
        RawItem raw = map.get(itemId);
        if (raw == null) {
            int base = resolveBaseId(itemId);
            if (base != itemId) {
                raw = map.get(base);
            }
        }
        if (raw == null) {
            return null;
        }
        return toInfo(raw);
    }

    private static Map<Integer, RawItem> load() {
        Map<Integer, RawItem> cached = BY_ID;
        if (cached != null) {
            return cached;
        }
        synchronized (ItemInfos.class) {
            if (BY_ID != null) {
                return BY_ID;
            }
            Map<Integer, RawItem> parsed = new HashMap<>();
            try (InputStream in = ItemInfos.class.getResourceAsStream("item-stats.json")) {
                if (in == null) {
                    log.warn("[ItemInfos] item-stats.json missing on classpath");
                    BY_ID = Collections.emptyMap();
                    return BY_ID;
                }
                Map<String, RawItem> raw = GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), MAP_TYPE);
                if (raw != null) {
                    for (Map.Entry<String, RawItem> e : raw.entrySet()) {
                        if (e.getKey() == null || e.getValue() == null) {
                            continue;
                        }
                        try {
                            parsed.put(Integer.parseInt(e.getKey()), e.getValue());
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
                log.info("[ItemInfos] loaded {} item rows", parsed.size());
            } catch (Exception e) {
                log.warn("[ItemInfos] failed to load item-stats.json: {}", e.toString());
            }
            BY_ID = parsed;
            return parsed;
        }
    }

    /**
     * Noted item → unnoted; bank placeholder → real item. Cosmetic skins stay on their own id
     * (the dump usually has a row for those).
     */
    private static int resolveBaseId(int itemId) {
        Integer mapped = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return itemId;
            }
            try {
                ItemComposition comp = c.getItemDefinition(itemId);
                if (comp == null) {
                    return itemId;
                }
                if (comp.getNote() != -1) {
                    int linked = comp.getLinkedNoteId();
                    if (linked > 0 && linked != itemId) {
                        return linked;
                    }
                }
                try {
                    int template = (Integer) ItemComposition.class.getMethod("getPlaceholderTemplateId").invoke(comp);
                    if (template != -1) {
                        int ph = (Integer) ItemComposition.class.getMethod("getPlaceholderId").invoke(comp);
                        if (ph > 0 && ph != itemId) {
                            return ph;
                        }
                    }
                } catch (Throwable ignored) {
                }
            } catch (Throwable ignored) {
            }
            return itemId;
        }, itemId);
        return mapped != null ? mapped : itemId;
    }

    private static ItemInfo toInfo(RawItem raw) {
        ItemInfo info = new ItemInfo();
        info.setWeight(raw.weight);
        info.setEquipable(raw.equipable);
        info.setGeLimit(raw.geLimit);
        RawEquip eq = raw.equipment;
        if (eq == null) {
            return info;
        }
        info.setEquipmentType(slotName(eq.slot));
        ItemInfo.EquipmentDefinition def = new ItemInfo.EquipmentDefinition();
        def.setSlot(eq.slot);
        def.setTwoHanded(eq.is2h);
        ItemInfo.EquipmentBonuses b = new ItemInfo.EquipmentBonuses();
        b.setAttStab(eq.astab);
        b.setAttSlash(eq.aslash);
        b.setAttCrush(eq.acrush);
        b.setAttMagic(eq.amagic);
        b.setAttRange(eq.arange);
        b.setDefStab(eq.dstab);
        b.setDefSlash(eq.dslash);
        b.setDefCrush(eq.dcrush);
        b.setDefMagic(eq.dmagic);
        b.setDefRange(eq.drange);
        b.setMeleeStrength(eq.str);
        b.setRangedStrength(eq.rstr);
        b.setMagicDamage(Math.round(eq.mdmg));
        b.setPrayer(eq.prayer);
        def.setBonuses(b);
        if (eq.aspeed != 0 || eq.is2h || eq.slot == 3) {
            ItemInfo.WeaponDefinition w = new ItemInfo.WeaponDefinition();
            w.setAttackSpeed(eq.aspeed);
            w.setTwoHanded(eq.is2h);
            def.setWeapon(w);
        }
        info.setEquipmentDefinition(def);
        return info;
    }

    private static String slotName(int slot) {
        switch (slot) {
            case 0:
                return "head";
            case 1:
                return "cape";
            case 2:
                return "neck";
            case 3:
                return "weapon";
            case 4:
                return "body";
            case 5:
                return "shield";
            case 7:
                return "legs";
            case 9:
                return "hands";
            case 10:
                return "feet";
            case 12:
                return "ring";
            case 13:
                return "ammo";
            default:
                return slot >= 0 ? "slot-" + slot : null;
        }
    }

    private static final class RawItem {
        double weight;
        boolean equipable;
        @SerializedName("ge_limit")
        int geLimit;
        RawEquip equipment;
    }

    private static final class RawEquip {
        int slot;
        boolean is2h;
        int astab;
        int aslash;
        int acrush;
        int amagic;
        int arange;
        int dstab;
        int dslash;
        int dcrush;
        int dmagic;
        int drange;
        int str;
        int rstr;
        float mdmg;
        int prayer;
        int aspeed;
    }
}
