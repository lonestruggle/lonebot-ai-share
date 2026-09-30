package net.storm.api.movement.pathfinder.model;

import net.runelite.api.Quest;
import net.runelite.api.Skill;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Pathfinder requirements (quests, skills, custom predicates).
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/sdk/movement/pathfinder/TransportLoader.html">Storm TransportLoader</a>
 */
public final class Requirements {

    private final List<Quest> quests = new ArrayList<>();
    private final List<SkillNeed> skills = new ArrayList<>();
    private final List<BooleanSupplier> extra = new ArrayList<>();

    public Requirements quest(Quest quest) {
        if (quest != null) {
            quests.add(quest);
        }
        return this;
    }

    public Requirements skill(Skill skill, int level) {
        if (skill != null && level > 0) {
            skills.add(new SkillNeed(skill, level));
        }
        return this;
    }

    public Requirements require(BooleanSupplier check) {
        if (check != null) {
            extra.add(check);
        }
        return this;
    }

    public List<Quest> getQuests() {
        return quests;
    }

    public List<SkillNeed> getSkills() {
        return skills;
    }

    public List<BooleanSupplier> getExtra() {
        return extra;
    }

    public static final class SkillNeed {
        public final Skill skill;
        public final int level;

        public SkillNeed(Skill skill, int level) {
            this.skill = skill;
            this.level = level;
        }
    }
}
