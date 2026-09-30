package net.storm.sdk.widgets;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.storm.api.domain.widgets.IWidget;
import net.storm.api.widgets.IProduction;
import net.storm.api.widgets.ProductionQuantity;
import net.storm.sdk.game.Static;
import net.storm.sdk.input.Keyboard;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Production / make-X interfaces (smithing, cooking, crafting, …).
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/api/widgets/IProduction.html">Storm IProduction</a>
 */
public final class Production {

    public static final int MULTI_SKILL_GROUP = InterfaceID.SKILLMULTI;
    public static final int SMITHING_GROUP = InterfaceID.SMITHING;

    public static final IProduction API = new Api();

    static {
        net.storm.api.Static.bindProduction(API);
        ProductionQuantity.ProductionQuantityAccess.bind(
                Production::quantityVisible,
                Production::selectQuantity,
                Production::quantitySelected
        );
    }

    /** Storm production quantity buttons — aliases {@link ProductionQuantity}. */
    public enum Quantity {
        ONE,
        FIVE,
        TEN,
        X,
        ALL
    }

    public Production() {
    }

    public static boolean isOpen() {
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            try {
                Widget smith = c.getWidget(WidgetInfo.SMITHING_INVENTORY_ITEMS_CONTAINER);
                if (visible(smith)) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
            Widget multi = c.getWidget(InterfaceID.Skillmulti.UNIVERSE);
            if (visible(multi)) {
                return true;
            }
            if (visible(c.getWidget(MULTI_SKILL_GROUP, 0)) || visible(c.getWidget(SMITHING_GROUP, 0))) {
                return true;
            }
            try {
                Widget chatTitle = c.getWidget(WidgetInfo.CHATBOX_TITLE);
                if (visible(chatTitle)) {
                    String t = chatTitle.getText();
                    if (t != null) {
                        String lower = t.toLowerCase();
                        if (lower.contains("how many") || lower.contains("choose") || lower.contains("cook")
                                || lower.contains("smelt") || lower.contains("smith")) {
                            return true;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            return false;
        }, false));
    }

    public static void continueSpace() {
        Keyboard.pressSpace();
    }

    public static void choosePreviousOption() {
        continueSpace();
    }

    public static boolean chooseOption(String optionText) {
        if (optionText == null || optionText.isBlank()) {
            return false;
        }
        String want = optionText.trim().toLowerCase();
        return chooseOption(t -> t != null && t.toLowerCase().contains(want));
    }

    public static boolean chooseOption(Predicate<String> option) {
        if (option == null) {
            return false;
        }
        Boolean found = Static.callOnClientThread(() -> {
            for (IWidget w : productionOptions()) {
                String t = optionText(w);
                if (t != null && option.test(t) && clickWidget(w)) {
                    return true;
                }
            }
            return false;
        }, false);
        if (Boolean.TRUE.equals(found)) {
            return true;
        }
        continueSpace();
        return false;
    }

    public static boolean chooseOption(int index) {
        Boolean found = Static.callOnClientThread(() -> {
            List<IWidget> opts = productionOptions();
            if (index < 0 || index >= opts.size()) {
                return false;
            }
            return clickWidget(opts.get(index));
        }, false);
        return Boolean.TRUE.equals(found);
    }

    public static boolean chooseQuantity(Quantity qty) {
        if (qty == null) {
            return false;
        }
        ProductionQuantity mapped;
        switch (qty) {
            case ONE:
                mapped = ProductionQuantity.ONE;
                break;
            case FIVE:
                mapped = ProductionQuantity.FIVE;
                break;
            case TEN:
                mapped = ProductionQuantity.TEN;
                break;
            case X:
                mapped = ProductionQuantity.X;
                break;
            case ALL:
                mapped = ProductionQuantity.ALL;
                break;
            default:
                return false;
        }
        return selectQuantity(mapped);
    }

    public static boolean selectQuantity(ProductionQuantity quantity) {
        if (quantity == null) {
            return false;
        }
        IWidget w = quantityWidget(quantity);
        if (w != null && clickWidget(w)) {
            return true;
        }
        switch (quantity) {
            case ONE:
                Keyboard.pressKey(KeyEvent.VK_1);
                return true;
            case FIVE:
                Keyboard.pressKey(KeyEvent.VK_5);
                return true;
            case TEN:
                Keyboard.type("10", true);
                return true;
            case ALL:
            case X:
                continueSpace();
                return true;
            case X_SET:
                selectOtherQuantity();
                return true;
            default:
                return false;
        }
    }

    public static void selectOtherQuantity() {
        IWidget w = quantityWidget(ProductionQuantity.X_SET);
        if (w != null && clickWidget(w)) {
            return;
        }
        IWidget other = Widgets.getFirst(x -> {
            String t = optionText(x);
            return t != null && (t.equalsIgnoreCase("other") || t.toLowerCase().contains("make x"));
        });
        if (other != null) {
            clickWidget(other);
        }
    }

    public static boolean selectMakeXQuantity(int quantity) {
        selectOtherQuantity();
        enterAmount(quantity);
        return true;
    }

    public static int getMakeXQuantity() {
        Integer n = Static.callOnClientThread(() -> {
            IWidget w = quantityWidget(ProductionQuantity.X);
            if (w == null) {
                return 0;
            }
            String t = optionText(w);
            if (t == null) {
                return 0;
            }
            String digits = t.replaceAll("[^0-9]", "");
            if (digits.isEmpty()) {
                return 0;
            }
            try {
                return Integer.parseInt(digits);
            } catch (NumberFormatException e) {
                return 0;
            }
        }, 0);
        return n != null ? n : 0;
    }

    public static ProductionQuantity getSelectedQuantity() {
        for (ProductionQuantity q : ProductionQuantity.values()) {
            if (quantitySelected(q)) {
                return q;
            }
        }
        return null;
    }

    public static boolean isEnterInputOpen() {
        return Dialog.isEnterInputOpen();
    }

    public static void enterAmount(int amount) {
        Dialog.enterAmount(amount);
    }

    public static void enterName(String input) {
        Dialog.enterName(input);
    }

    public static void selectItem(String name) {
        chooseOption(name);
    }

    public static void selectItem(int itemId) {
        Static.callOnClientThread(() -> {
            IWidget hit = Widgets.getFirst(w -> w.getItemId() == itemId);
            if (hit != null) {
                clickWidget(hit);
            }
            return true;
        }, false);
    }

    static boolean quantityVisible(ProductionQuantity q) {
        IWidget w = quantityWidget(q);
        return w != null && !w.isHidden();
    }

    static boolean quantitySelected(ProductionQuantity q) {
        IWidget w = quantityWidget(q);
        if (w == null || w.isHidden()) {
            return false;
        }
        return Static.callOnClientThread(() -> {
            if (!(w instanceof RlWidget)) {
                return false;
            }
            Widget raw = ((RlWidget) w).raw();
            try {
                return raw.getOpacity() == 0 && raw.getBorderType() > 0;
            } catch (Throwable t) {
                return false;
            }
        }, false);
    }

    private static IWidget quantityWidget(ProductionQuantity q) {
        if (q == null) {
            return null;
        }
        int packed;
        switch (q) {
            case ONE:
                packed = InterfaceID.Skillmulti._1;
                break;
            case FIVE:
                packed = InterfaceID.Skillmulti._5;
                break;
            case TEN:
                packed = InterfaceID.Skillmulti._10;
                break;
            case X_SET:
                packed = InterfaceID.Skillmulti.OTHER;
                break;
            case X:
                packed = InterfaceID.Skillmulti.X;
                break;
            case ALL:
                packed = InterfaceID.Skillmulti.ALL;
                break;
            default:
                return null;
        }
        return Widgets.get(packed);
    }

    private static List<IWidget> productionOptions() {
        List<IWidget> out = new ArrayList<>();
        Client c = Static.getClient();
        if (c == null) {
            return out;
        }
        for (int child = 0; child < 64; child++) {
            Widget w = c.getWidget(MULTI_SKILL_GROUP, child);
            if (!visible(w)) {
                continue;
            }
            IWidget wrap = Widgets.wrap(w);
            if (wrap != null && optionText(wrap) != null) {
                out.add(wrap);
            }
            Widget[] kids = w.getDynamicChildren();
            if (kids == null) {
                kids = w.getChildren();
            }
            if (kids == null) {
                continue;
            }
            for (Widget k : kids) {
                if (!visible(k)) {
                    continue;
                }
                IWidget childWrap = Widgets.wrap(k);
                if (childWrap != null && optionText(childWrap) != null) {
                    out.add(childWrap);
                }
            }
        }
        return out;
    }

    private static String optionText(IWidget w) {
        if (w == null) {
            return null;
        }
        String t = w.getText();
        if (t != null && !t.isBlank()) {
            return WidgetText.strip(t);
        }
        String n = w.getName();
        return n == null || n.isBlank() ? null : WidgetText.strip(n);
    }

    private static boolean clickWidget(IWidget w) {
        if (w == null) {
            return false;
        }
        if (w.interact("Select") || w.interact("Make") || w.interact(optionText(w))) {
            return true;
        }
        if (!(w instanceof RlWidget)) {
            return false;
        }
        Widget raw = ((RlWidget) w).raw();
        try {
            net.runelite.api.Point p = raw.getCanvasLocation();
            if (p == null) {
                return false;
            }
            int x = p.getX() + Math.max(2, raw.getWidth() / 2);
            int y = p.getY() + Math.max(2, raw.getHeight() / 2);
            return net.storm.sdk.interact.mouse.MouseManager.interactAt(
                    new net.runelite.api.Point(x, y));
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean visible(Widget w) {
        return w != null && !w.isHidden();
    }

    private static final class Api implements IProduction {
        @Override
        public boolean isOpen() {
            return Production.isOpen();
        }

        @Override
        public void chooseOption(Predicate<String> option) {
            Production.chooseOption(option);
        }

        @Override
        public void selectOtherQuantity() {
            Production.selectOtherQuantity();
        }

        @Override
        public void chooseOption(int index) {
            Production.chooseOption(index);
        }

        @Override
        public void choosePreviousOption() {
            Production.choosePreviousOption();
        }

        @Override
        public boolean isEnterInputOpen() {
            return Production.isEnterInputOpen();
        }

        @Override
        public void enterAmount(int amount) {
            Production.enterAmount(amount);
        }

        @Override
        public void enterName(String input) {
            Production.enterName(input);
        }

        @Override
        public void selectItem(String name) {
            Production.selectItem(name);
        }

        @Override
        public void selectItem(int itemId) {
            Production.selectItem(itemId);
        }

        @Override
        public int getMakeXQuantity() {
            return Production.getMakeXQuantity();
        }

        @Override
        public boolean selectMakeXQuantity(int quantity) {
            return Production.selectMakeXQuantity(quantity);
        }

        @Override
        public ProductionQuantity getSelectedQuantity() {
            return Production.getSelectedQuantity();
        }

        @Override
        public boolean selectQuantity(ProductionQuantity quantity) {
            return Production.selectQuantity(quantity);
        }
    }
}
