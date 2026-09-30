package net.storm.api.widgets;

/**
 * Quantity buttons on the multi-skill production interface (group {@code SKILLMULTI}).
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/api/widgets/ProductionQuantity.html">Storm ProductionQuantity</a>
 */
public enum ProductionQuantity {
    ONE,
    FIVE,
    TEN,
    X_SET,
    X,
    ALL;

    public boolean isVisible() {
        return ProductionQuantityAccess.isVisible(this);
    }

    public boolean select() {
        return ProductionQuantityAccess.select(this);
    }

    public boolean isSelected() {
        return ProductionQuantityAccess.isSelected(this);
    }

    /**
     * Bound by SDK {@code Production} so this API enum can click real widgets.
     */
    public static final class ProductionQuantityAccess {
        private static VisibleFn visible = q -> false;
        private static SelectFn select = q -> false;
        private static SelectedFn selected = q -> false;

        private ProductionQuantityAccess() {
        }

        public static void bind(VisibleFn vis, SelectFn sel, SelectedFn isSel) {
            if (vis != null) {
                visible = vis;
            }
            if (sel != null) {
                select = sel;
            }
            if (isSel != null) {
                selected = isSel;
            }
        }

        static boolean isVisible(ProductionQuantity q) {
            return visible.test(q);
        }

        static boolean select(ProductionQuantity q) {
            return select.test(q);
        }

        static boolean isSelected(ProductionQuantity q) {
            return selected.test(q);
        }

        @FunctionalInterface
        public interface VisibleFn {
            boolean test(ProductionQuantity q);
        }

        @FunctionalInterface
        public interface SelectFn {
            boolean test(ProductionQuantity q);
        }

        @FunctionalInterface
        public interface SelectedFn {
            boolean test(ProductionQuantity q);
        }
    }
}
