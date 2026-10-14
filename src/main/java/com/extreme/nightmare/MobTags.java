package com.extreme.nightmare;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Identificación de mobs especiales mediante PersistentDataContainer (nunca por nombre). */
public final class MobTags {

    public static NamespacedKey TYPE, ELITE, GEN, EVENT, NEXT_MULT, NEXT_ABILITY, ANGRY_UNTIL, OURS, ARROW, BOSS;

    private MobTags() {}

    public static void init(JavaPlugin p) {
        TYPE = new NamespacedKey(p, "nm_type");
        ELITE = new NamespacedKey(p, "nm_elite");
        GEN = new NamespacedKey(p, "nm_gen");
        EVENT = new NamespacedKey(p, "nm_event");
        NEXT_MULT = new NamespacedKey(p, "nm_next_mult");
        NEXT_ABILITY = new NamespacedKey(p, "nm_next_ability");
        ANGRY_UNTIL = new NamespacedKey(p, "nm_angry_until");
        OURS = new NamespacedKey(p, "nm_ours");
        ARROW = new NamespacedKey(p, "nm_arrow");
        BOSS = new NamespacedKey(p, "nm_boss");
    }

    private static PersistentDataContainer pdc(Entity e) {
        return e.getPersistentDataContainer();
    }

    public static void setType(Entity e, String type) { pdc(e).set(TYPE, PersistentDataType.STRING, type); }
    public static String getType(Entity e) { return pdc(e).get(TYPE, PersistentDataType.STRING); }
    public static boolean isType(Entity e, String type) { return type.equals(getType(e)); }

    public static void setOurs(Entity e) { pdc(e).set(OURS, PersistentDataType.BYTE, (byte) 1); }
    public static boolean isOurs(Entity e) { return pdc(e).has(OURS, PersistentDataType.BYTE); }

    public static void setElite(Entity e) { pdc(e).set(ELITE, PersistentDataType.BYTE, (byte) 1); }
    public static boolean isElite(Entity e) { return pdc(e).has(ELITE, PersistentDataType.BYTE); }

    public static void setBoss(Entity e, String key) { pdc(e).set(BOSS, PersistentDataType.STRING, key); }
    public static String getBoss(Entity e) { return pdc(e).get(BOSS, PersistentDataType.STRING); }

    public static void setEvent(Entity e, String id) { pdc(e).set(EVENT, PersistentDataType.STRING, id); }
    public static String getEvent(Entity e) { return pdc(e).get(EVENT, PersistentDataType.STRING); }

    public static int getGen(Entity e) { return pdc(e).getOrDefault(GEN, PersistentDataType.INTEGER, 0); }
    public static void setGen(Entity e, int g) { pdc(e).set(GEN, PersistentDataType.INTEGER, g); }

    public static long getLong(Entity e, NamespacedKey k) { return pdc(e).getOrDefault(k, PersistentDataType.LONG, 0L); }
    public static void setLong(Entity e, NamespacedKey k, long v) { pdc(e).set(k, PersistentDataType.LONG, v); }

    public static void setArrow(Entity arrow, String kind) { pdc(arrow).set(ARROW, PersistentDataType.STRING, kind); }
    public static String getArrow(Entity arrow) { return pdc(arrow).get(ARROW, PersistentDataType.STRING); }
}
