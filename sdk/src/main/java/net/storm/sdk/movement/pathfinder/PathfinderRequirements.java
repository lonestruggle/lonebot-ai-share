package net.storm.sdk.movement.pathfinder;

import net.runelite.api.Quest;
import net.storm.api.movement.pathfinder.model.Requirements;
import net.storm.sdk.game.Skills;
import net.storm.sdk.quests.Quests;

import java.util.function.BooleanSupplier;

/**
 * Evaluates {@link Requirements} against live quest/skill state.
 */
public final class PathfinderRequirements {

    private PathfinderRequirements() {
    }

    public static boolean met(Requirements requirements) {
        if (requirements == null) {
            return true;
        }
        for (Quest q : requirements.getQuests()) {
            if (q != null && !Quests.isFinished(q)) {
                return false;
            }
        }
        for (Requirements.SkillNeed need : requirements.getSkills()) {
            if (need == null || need.skill == null) {
                continue;
            }
            if (Skills.getLevel(need.skill) < need.level) {
                return false;
            }
        }
        for (BooleanSupplier extra : requirements.getExtra()) {
            if (extra != null && !extra.getAsBoolean()) {
                return false;
            }
        }
        return true;
    }
}
