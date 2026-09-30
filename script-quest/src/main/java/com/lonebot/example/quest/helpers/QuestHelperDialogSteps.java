package com.lonebot.example.quest.helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Ordered exact dialog choices (CombatBot {@code QuestHelperDialogSteps}).
 * No wildcard {@code Yes} — only the listed strings are clicked.
 */
public final class QuestHelperDialogSteps {

    private final List<String> steps;

    public QuestHelperDialogSteps(String... steps) {
        if (steps == null || steps.length == 0) {
            this.steps = Collections.emptyList();
        } else {
            List<String> out = new ArrayList<>();
            for (String s : steps) {
                if (s != null && !s.isBlank()) {
                    out.add(s.trim());
                }
            }
            this.steps = Collections.unmodifiableList(out);
        }
    }

    public static QuestHelperDialogSteps of(String... steps) {
        return new QuestHelperDialogSteps(steps);
    }

    public List<String> steps() {
        return steps;
    }

    public boolean isEmpty() {
        return steps.isEmpty();
    }

    public QuestHelperDialogSteps plus(String... extra) {
        List<String> all = new ArrayList<>(steps);
        if (extra != null) {
            all.addAll(Arrays.asList(extra));
        }
        return new QuestHelperDialogSteps(all.toArray(new String[0]));
    }
}
