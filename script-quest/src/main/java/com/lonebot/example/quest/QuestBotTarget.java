package com.lonebot.example.quest;

/**
 * CombatBot {@code QuestBotTarget} — één QUEST-mode + dropdown.
 */
public enum QuestBotTarget {
    ROTATION("Rotatie (volgende haalbare F2P)"),
    COOKS_ASSISTANT("Cook's Assistant"),
    GOBLIN_DIPLOMACY("Goblin Diplomacy"),
    ROMEO_AND_JULIET("Romeo & Juliet"),
    RUNE_MYSTERIES("Rune Mysteries"),
    DORICS("Doric's Quest"),
    VAMPYRE_SLAYER("Vampire Slayer"),
    TUTORIAL("Tutorial Island");

    public final String label;

    QuestBotTarget(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }

    public static QuestBotTarget from(String raw) {
        if (raw == null || raw.isBlank()) {
            return ROTATION;
        }
        String u = raw.trim().toUpperCase().replace(' ', '_').replace('-', '_');
        if ("QUEST".equals(u) || "QUESTER".equals(u) || "ROTATE".equals(u) || "AUTO".equals(u)) {
            return ROTATION;
        }
        if ("COOKS".equals(u) || "COOK".equals(u) || "COOKS_ASSISTANT".equals(u)) {
            return COOKS_ASSISTANT;
        }
        if ("GOBLIN".equals(u) || "GOBLIN_DIPLOMACY".equals(u)) {
            return GOBLIN_DIPLOMACY;
        }
        if ("ROMEO".equals(u) || "ROMEO_AND_JULIET".equals(u) || "ROMEO__JULIET".equals(u)
                || "JULIET".equals(u)) {
            return ROMEO_AND_JULIET;
        }
        if ("RUNE".equals(u) || "RUNE_MYSTERIES".equals(u) || "RUNEMYSTERIES".equals(u)) {
            return RUNE_MYSTERIES;
        }
        if ("DORIC".equals(u) || "DORICS".equals(u) || "DORICS_QUEST".equals(u)) {
            return DORICS;
        }
        if ("VAMPYRE_SLAYER".equals(u) || "VAMPIRE_SLAYER".equals(u) || "VAMPIRE".equals(u)
                || "VAMPYRE".equals(u)) {
            return VAMPYRE_SLAYER;
        }
        if ("TUTORIAL".equals(u) || "TUTORIAL_ISLAND".equals(u)) {
            return TUTORIAL;
        }
        for (QuestBotTarget t : values()) {
            if (t.name().equalsIgnoreCase(u) || t.label.equalsIgnoreCase(raw.trim())) {
                return t;
            }
        }
        return ROTATION;
    }

    /** Accounts {@code preferredScript} → target (QUEST/QUESTER = rotatie). */
    public static QuestBotTarget fromPreferredScript(String preferred) {
        return from(preferred);
    }

    public boolean isRotation() {
        return this == ROTATION;
    }
}
