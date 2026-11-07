package view;

import javax.swing.*;
import java.awt.*;

/**
 * A {@link FlowLayout} whose preferred size accounts for wrapped rows.
 *
 * <p>A plain FlowLayout reports the height of a single row. When the container is narrower than
 * its components, the wrapped rows lie outside the container and the buttons in them are not
 * visible. This layout computes the height for the actual width, so wrapped buttons stay visible.</p>
 */
public class WrapLayout extends FlowLayout {

    /**
     * Creates a wrapping flow layout.
     *
     * @param align alignment, e.g. {@link FlowLayout#CENTER}
     * @param hgap  horizontal gap
     * @param vgap  vertical gap
     */
    public WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return layoutSize(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension minimum = layoutSize(target, false);
        minimum.width -= getHgap() + 1;
        return minimum;
    }

    private Dimension layoutSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            Container sizeSource = target;
            while (sizeSource.getSize().width == 0 && sizeSource.getParent() != null) {
                sizeSource = sizeSource.getParent();
            }
            int targetWidth = sizeSource.getSize().width;
            if (targetWidth == 0) targetWidth = Integer.MAX_VALUE;

            Insets insets = target.getInsets();
            int horizontalInsetsAndGap = insets.left + insets.right + getHgap() * 2;
            int maxWidth = targetWidth - horizontalInsetsAndGap;

            Dimension dim = new Dimension(0, 0);
            int rowWidth = 0;
            int rowHeight = 0;
            for (Component m : target.getComponents()) {
                if (!m.isVisible()) continue;
                Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();
                if (rowWidth + d.width > maxWidth) {
                    addRow(dim, rowWidth, rowHeight);
                    rowWidth = 0;
                    rowHeight = 0;
                }
                if (rowWidth != 0) rowWidth += getHgap();
                rowWidth += d.width;
                rowHeight = Math.max(rowHeight, d.height);
            }
            addRow(dim, rowWidth, rowHeight);

            dim.width += horizontalInsetsAndGap;
            dim.height += insets.top + insets.bottom + getVgap() * 2;

            // inside a scroll pane the width must not force a horizontal scroll bar
            Container scrollPane = SwingUtilities.getAncestorOfClass(JScrollPane.class, target);
            if (scrollPane != null && target.isValid()) {
                dim.width -= getHgap() + 1;
            }
            return dim;
        }
    }

    private void addRow(Dimension dim, int rowWidth, int rowHeight) {
        dim.width = Math.max(dim.width, rowWidth);
        if (dim.height > 0) dim.height += getVgap();
        dim.height += rowHeight;
    }
}
