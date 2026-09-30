package net.storm.sdk.items.info;

/**
 * Item metadata: weight, slot, combat bonuses, weapon speed.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/sdk/items/info/ItemInfo.html">Storm ItemInfo</a>
 */
public class ItemInfo {

    private double weight;
    private String equipmentType;
    private EquipmentDefinition equipmentDefinition;
    private boolean equipable;
    private int geLimit;

    public ItemInfo() {
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    public String getEquipmentType() {
        return equipmentType;
    }

    public void setEquipmentType(String equipmentType) {
        this.equipmentType = equipmentType;
    }

    public EquipmentDefinition getEquipmentDefinition() {
        return equipmentDefinition;
    }

    public void setEquipmentDefinition(EquipmentDefinition equipmentDefinition) {
        this.equipmentDefinition = equipmentDefinition;
    }

    public boolean isEquipable() {
        return equipable;
    }

    public void setEquipable(boolean equipable) {
        this.equipable = equipable;
    }

    public int getGeLimit() {
        return geLimit;
    }

    public void setGeLimit(int geLimit) {
        this.geLimit = geLimit;
    }

    /**
     * Combat bonuses (stab/slash/crush/magic/range attack &amp; defence, strength, prayer).
     */
    public static class EquipmentBonuses {

        private int attStab;
        private int attSlash;
        private int attCrush;
        private int attMagic;
        private int attRange;
        private int defStab;
        private int defSlash;
        private int defCrush;
        private int defMagic;
        private int defRange;
        private int meleeStrength;
        private int rangedStrength;
        private int magicDamage;
        private int prayer;

        public EquipmentBonuses() {
        }

        public int getAttStab() {
            return attStab;
        }

        public void setAttStab(int attStab) {
            this.attStab = attStab;
        }

        public int getAttSlash() {
            return attSlash;
        }

        public void setAttSlash(int attSlash) {
            this.attSlash = attSlash;
        }

        public int getAttCrush() {
            return attCrush;
        }

        public void setAttCrush(int attCrush) {
            this.attCrush = attCrush;
        }

        public int getAttMagic() {
            return attMagic;
        }

        public void setAttMagic(int attMagic) {
            this.attMagic = attMagic;
        }

        public int getAttRange() {
            return attRange;
        }

        public void setAttRange(int attRange) {
            this.attRange = attRange;
        }

        public int getDefStab() {
            return defStab;
        }

        public void setDefStab(int defStab) {
            this.defStab = defStab;
        }

        public int getDefSlash() {
            return defSlash;
        }

        public void setDefSlash(int defSlash) {
            this.defSlash = defSlash;
        }

        public int getDefCrush() {
            return defCrush;
        }

        public void setDefCrush(int defCrush) {
            this.defCrush = defCrush;
        }

        public int getDefMagic() {
            return defMagic;
        }

        public void setDefMagic(int defMagic) {
            this.defMagic = defMagic;
        }

        public int getDefRange() {
            return defRange;
        }

        public void setDefRange(int defRange) {
            this.defRange = defRange;
        }

        public int getMeleeStrength() {
            return meleeStrength;
        }

        public void setMeleeStrength(int meleeStrength) {
            this.meleeStrength = meleeStrength;
        }

        public int getRangedStrength() {
            return rangedStrength;
        }

        public void setRangedStrength(int rangedStrength) {
            this.rangedStrength = rangedStrength;
        }

        public int getMagicDamage() {
            return magicDamage;
        }

        public void setMagicDamage(int magicDamage) {
            this.magicDamage = magicDamage;
        }

        public int getPrayer() {
            return prayer;
        }

        public void setPrayer(int prayer) {
            this.prayer = prayer;
        }

        public EquipmentBonuses plus(EquipmentBonuses bonuses) {
            EquipmentBonuses out = new EquipmentBonuses();
            if (bonuses == null) {
                out.attStab = attStab;
                out.attSlash = attSlash;
                out.attCrush = attCrush;
                out.attMagic = attMagic;
                out.attRange = attRange;
                out.defStab = defStab;
                out.defSlash = defSlash;
                out.defCrush = defCrush;
                out.defMagic = defMagic;
                out.defRange = defRange;
                out.meleeStrength = meleeStrength;
                out.rangedStrength = rangedStrength;
                out.magicDamage = magicDamage;
                out.prayer = prayer;
                return out;
            }
            out.attStab = attStab + bonuses.attStab;
            out.attSlash = attSlash + bonuses.attSlash;
            out.attCrush = attCrush + bonuses.attCrush;
            out.attMagic = attMagic + bonuses.attMagic;
            out.attRange = attRange + bonuses.attRange;
            out.defStab = defStab + bonuses.defStab;
            out.defSlash = defSlash + bonuses.defSlash;
            out.defCrush = defCrush + bonuses.defCrush;
            out.defMagic = defMagic + bonuses.defMagic;
            out.defRange = defRange + bonuses.defRange;
            out.meleeStrength = meleeStrength + bonuses.meleeStrength;
            out.rangedStrength = rangedStrength + bonuses.rangedStrength;
            out.magicDamage = magicDamage + bonuses.magicDamage;
            out.prayer = prayer + bonuses.prayer;
            return out;
        }
    }

    /**
     * Slot + bonuses + optional weapon block.
     */
    public static class EquipmentDefinition {

        private int slot;
        private boolean twoHanded;
        private EquipmentBonuses bonuses;
        private WeaponDefinition weapon;

        public EquipmentDefinition() {
        }

        public int getSlot() {
            return slot;
        }

        public void setSlot(int slot) {
            this.slot = slot;
        }

        public boolean isTwoHanded() {
            return twoHanded;
        }

        public void setTwoHanded(boolean twoHanded) {
            this.twoHanded = twoHanded;
        }

        public EquipmentBonuses getBonuses() {
            return bonuses;
        }

        public void setBonuses(EquipmentBonuses bonuses) {
            this.bonuses = bonuses;
        }

        public WeaponDefinition getWeapon() {
            return weapon;
        }

        public void setWeapon(WeaponDefinition weapon) {
            this.weapon = weapon;
        }
    }

    /**
     * Weapon speed / 2h from the stats dump. Animations and attack-distance are not in that dump
     * — those getters stay 0 / null rather than guessed.
     */
    public static class WeaponDefinition {

        private int attackSpeed;
        private boolean twoHanded;
        private int attackDistance;
        private int standAnimation;
        private int attackAnimation;

        public WeaponDefinition() {
        }

        public int getAttackSpeed() {
            return attackSpeed;
        }

        public void setAttackSpeed(int attackSpeed) {
            this.attackSpeed = attackSpeed;
        }

        public boolean isTwoHanded() {
            return twoHanded;
        }

        public void setTwoHanded(boolean twoHanded) {
            this.twoHanded = twoHanded;
        }

        /** 0 = unknown (not present in the stats dump). */
        public int getAttackDistance() {
            return attackDistance;
        }

        public void setAttackDistance(int attackDistance) {
            this.attackDistance = attackDistance;
        }

        public int getStandAnimation() {
            return standAnimation;
        }

        public void setStandAnimation(int standAnimation) {
            this.standAnimation = standAnimation;
        }

        public int getAttackAnimation() {
            return attackAnimation;
        }

        public void setAttackAnimation(int attackAnimation) {
            this.attackAnimation = attackAnimation;
        }
    }
}
