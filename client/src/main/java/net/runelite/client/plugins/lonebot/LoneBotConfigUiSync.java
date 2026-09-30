package net.runelite.client.plugins.lonebot;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntFunction;

/**
 * Houdt client-sidebar en Control-venster op dezelfde {@code lonebot} config-keys.
 * Schrijven blijft ConfigManager; bij {@code ConfigChanged} worden alle widgets bijgewerkt.
 */
final class LoneBotConfigUiSync {

    private static final AtomicBoolean APPLYING = new AtomicBoolean(false);
    private static final ConcurrentHashMap<String, CopyOnWriteArrayList<Binding>> MAP =
            new ConcurrentHashMap<>();

    private interface Binding {
        void apply(String raw);

        boolean dead();
    }

    private LoneBotConfigUiSync() {
    }

    static boolean applying() {
        return APPLYING.get();
    }

    static void apply(String key, String newValue) {
        if (key == null || key.isBlank()) {
            return;
        }
        Runnable r = () -> {
            CopyOnWriteArrayList<Binding> list = MAP.get(key);
            if (list == null || list.isEmpty()) {
                return;
            }
            APPLYING.set(true);
            try {
                list.removeIf(Binding::dead);
                for (Binding b : list) {
                    try {
                        b.apply(newValue);
                    } catch (Throwable ignored) {
                    }
                }
            } finally {
                APPLYING.set(false);
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }

    static void bool(JCheckBox cb, String key) {
        if (cb == null || key == null) {
            return;
        }
        add(key, new Binding() {
            final WeakReference<JCheckBox> ref = new WeakReference<>(cb);

            @Override
            public void apply(String raw) {
                JCheckBox c = ref.get();
                if (c == null) {
                    return;
                }
                boolean v = parseBool(raw);
                if (c.isSelected() != v) {
                    c.setSelected(v);
                }
            }

            @Override
            public boolean dead() {
                return ref.get() == null;
            }
        });
    }

    /** Checkbox die aan staat als de boolean-key uit staat (Beste boom / Beste vis). */
    static void boolInverted(JCheckBox cb, String key) {
        if (cb == null || key == null) {
            return;
        }
        add(key, new Binding() {
            final WeakReference<JCheckBox> ref = new WeakReference<>(cb);

            @Override
            public void apply(String raw) {
                JCheckBox c = ref.get();
                if (c == null) {
                    return;
                }
                boolean v = !parseBool(raw);
                if (c.isSelected() != v) {
                    c.setSelected(v);
                }
            }

            @Override
            public boolean dead() {
                return ref.get() == null;
            }
        });
    }

    static void integer(JSpinner sp, String key) {
        if (sp == null || key == null) {
            return;
        }
        add(key, new Binding() {
            final WeakReference<JSpinner> ref = new WeakReference<>(sp);

            @Override
            public void apply(String raw) {
                JSpinner s = ref.get();
                if (s == null) {
                    return;
                }
                Integer v = parseInt(raw);
                if (v == null) {
                    return;
                }
                Object cur = s.getValue();
                int now = cur instanceof Number ? ((Number) cur).intValue() : Integer.MIN_VALUE;
                if (now != v) {
                    s.setValue(v);
                }
            }

            @Override
            public boolean dead() {
                return ref.get() == null;
            }
        });
    }

    static void slider(JSlider sl, JLabel valueLabel, String key) {
        slider(sl, valueLabel, key, null);
    }

    static void slider(JSlider sl, JLabel valueLabel, String key, IntFunction<String> format) {
        if (sl == null || key == null) {
            return;
        }
        add(key, new Binding() {
            final WeakReference<JSlider> sref = new WeakReference<>(sl);
            final WeakReference<JLabel> lref = valueLabel != null ? new WeakReference<>(valueLabel) : null;
            final IntFunction<String> fmt = format;

            @Override
            public void apply(String raw) {
                JSlider s = sref.get();
                if (s == null) {
                    return;
                }
                Integer v = parseInt(raw);
                if (v == null) {
                    return;
                }
                int clamped = Math.max(s.getMinimum(), Math.min(s.getMaximum(), v));
                if (s.getValue() != clamped) {
                    s.setValue(clamped);
                }
                JLabel lbl = lref != null ? lref.get() : null;
                if (lbl != null) {
                    lbl.setText(fmt != null ? fmt.apply(clamped) : String.valueOf(clamped));
                }
            }

            @Override
            public boolean dead() {
                return sref.get() == null;
            }
        });
    }

    static void combo(JComboBox<?> combo, String key) {
        if (combo == null || key == null) {
            return;
        }
        add(key, new Binding() {
            final WeakReference<JComboBox<?>> ref = new WeakReference<>(combo);

            @Override
            public void apply(String raw) {
                JComboBox<?> c = ref.get();
                if (c == null || raw == null) {
                    return;
                }
                Object match = findComboItem(c, raw);
                if (match == null) {
                    return;
                }
                Object cur = c.getSelectedItem();
                if (cur == null || !String.valueOf(cur).equals(String.valueOf(match))) {
                    c.setSelectedItem(match);
                }
            }

            @Override
            public boolean dead() {
                return ref.get() == null;
            }
        });
    }

    /** Sync Bank/FM/Drop-combo wanneer legacy {@code wcFiremaking}/{@code wcDropLogs} wijzigen. */
    static void wcLogModeCombo(JComboBox<LoneBotConfig.WcLogMode> combo) {
        if (combo == null) {
            return;
        }
        Binding b = new Binding() {
            final WeakReference<JComboBox<LoneBotConfig.WcLogMode>> ref = new WeakReference<>(combo);

            @Override
            public void apply(String raw) {
                JComboBox<LoneBotConfig.WcLogMode> c = ref.get();
                if (c == null) {
                    return;
                }
                LoneBotConfig.WcLogMode want = LoneBotBootstrapPlugin.currentWcLogMode();
                Object cur = c.getSelectedItem();
                if (cur != want) {
                    c.setSelectedItem(want);
                }
            }

            @Override
            public boolean dead() {
                return ref.get() == null;
            }
        };
        add("wcFiremaking", b);
        add("wcDropLogs", b);
    }

    static void text(JTextComponent field, String key) {
        if (field == null || key == null) {
            return;
        }
        add(key, new Binding() {
            final WeakReference<JTextComponent> ref = new WeakReference<>(field);

            @Override
            public void apply(String raw) {
                JTextComponent f = ref.get();
                if (f == null || f.hasFocus()) {
                    return;
                }
                String want = raw != null ? raw : "";
                if (!want.equals(f.getText())) {
                    f.setText(want);
                }
            }

            @Override
            public boolean dead() {
                return ref.get() == null;
            }
        });
    }

    static void enableWhen(java.awt.Component c, String key, boolean whenTrue) {
        if (c == null || key == null) {
            return;
        }
        add(key, new Binding() {
            final WeakReference<java.awt.Component> ref = new WeakReference<>(c);
            final boolean want = whenTrue;

            @Override
            public void apply(String raw) {
                java.awt.Component comp = ref.get();
                if (comp == null) {
                    return;
                }
                boolean on = parseBool(raw);
                boolean en = want == on;
                if (comp.isEnabled() != en) {
                    comp.setEnabled(en);
                }
            }

            @Override
            public boolean dead() {
                return ref.get() == null;
            }
        });
    }

    private static void add(String key, Binding b) {
        MAP.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(b);
    }

    private static boolean parseBool(String raw) {
        return raw != null && Boolean.parseBoolean(raw.trim());
    }

    private static Integer parseInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Object findComboItem(JComboBox<?> c, String raw) {
        String want = raw.trim();
        for (int i = 0; i < c.getItemCount(); i++) {
            Object item = c.getItemAt(i);
            if (item == null) {
                continue;
            }
            if (item instanceof Enum) {
                Enum<?> en = (Enum<?>) item;
                if (en.name().equalsIgnoreCase(want)) {
                    return item;
                }
            }
            if (want.equals(String.valueOf(item)) || want.equalsIgnoreCase(item.toString())) {
                return item;
            }
        }
        return null;
    }
}
