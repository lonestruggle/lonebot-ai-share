package net.runelite.client.plugins.lonebot;

import net.runelite.client.config.ConfigManager;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JSpinner;
import javax.swing.JTextArea;

/**
 * DZ-rijen die naar dezelfde ConfigManager-keys schrijven als het Control-venster.
 */
final class LoneBotDzBind {

    private final ConfigManager cm;
    private final LoneBotDzLook.Section sec;

    LoneBotDzBind(ConfigManager cm, LoneBotDzLook.Section sec) {
        this.cm = cm;
        this.sec = sec;
    }

    void bool(String label, String key, boolean initial, Runnable after) {
        JCheckBox cb = LoneBotDzLook.box(initial);
        LoneBotConfigUiSync.bool(cb, key);
        cb.addActionListener(e -> {
            if (LoneBotConfigUiSync.applying()) {
                return;
            }
            cm.setConfiguration("lonebot", key, cb.isSelected());
            if (after != null) {
                after.run();
            }
        });
        sec.addItem(LoneBotDzLook.itemRow(label, cb));
    }

    void integer(String label, String key, int value, int min, int max, Runnable after) {
        JSpinner sp = LoneBotDzLook.spinner(value, min, max);
        LoneBotConfigUiSync.integer(sp, key);
        sp.addChangeListener(e -> {
            if (LoneBotConfigUiSync.applying()) {
                return;
            }
            cm.setConfiguration("lonebot", key, sp.getValue());
            if (after != null) {
                after.run();
            }
        });
        sec.addItem(LoneBotDzLook.itemRow(label, sp));
    }

    void combo(String label, String key, String[] items, String selected, Runnable after) {
        JComboBox<String> c = LoneBotDzLook.combo(items);
        if (selected != null) {
            c.setSelectedItem(selected);
        }
        LoneBotConfigUiSync.combo(c, key);
        c.addActionListener(e -> {
            if (LoneBotConfigUiSync.applying()) {
                return;
            }
            Object v = c.getSelectedItem();
            if (v != null) {
                cm.setConfiguration("lonebot", key, v.toString());
                if (after != null) {
                    after.run();
                }
            }
        });
        sec.addItem(LoneBotDzLook.itemRow(label, c));
    }

    <T extends Enum<T>> void enums(String label, String key, T[] values, T selected, Runnable after) {
        JComboBox<T> c = LoneBotDzLook.comboOf(values, selected);
        LoneBotConfigUiSync.combo(c, key);
        c.addActionListener(e -> {
            if (LoneBotConfigUiSync.applying()) {
                return;
            }
            Object v = c.getSelectedItem();
            if (v != null) {
                cm.setConfiguration("lonebot", key, v);
                if (after != null) {
                    after.run();
                }
            }
        });
        sec.addItem(LoneBotDzLook.itemRow(label, c));
    }

    void text(String label, String key, String initial, Runnable after) {
        JTextArea f = LoneBotDzLook.growingList(initial);
        LoneBotConfigUiSync.text(f, key);
        Runnable save = () -> {
            if (LoneBotConfigUiSync.applying()) {
                return;
            }
            cm.setConfiguration("lonebot", key, f.getText().trim());
            if (after != null) {
                after.run();
            }
        };
        f.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                save.run();
            }
        });
        sec.addItem(LoneBotDzLook.itemRowFill(label, f));
    }
}
