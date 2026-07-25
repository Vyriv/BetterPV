package dev.vy.betterpv.client.gui.nav;

import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.PvTooltip;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * NEU-style frame tabs: icons sit on the outside of the panel edge.
 */
public final class IconButtonBar {
	public static final int TAB = 26;
	public static final int GAP = 1;
	/** Pixels of the tab that sit on top of the panel border. */
	public static final int SEAM = 3;

	public static final int BTN = TAB;

	private final List<Hit> hits = new ArrayList<>();

	public void clearHits() {
		this.hits.clear();
	}

	public boolean click(double mouseX, double mouseY) {
		for (Hit hit : this.hits) {
			if (hit.contains(mouseX, mouseY)) {
				hit.onClick.run();
				return true;
			}
		}
		return false;
	}

	public void drawInnerButton(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		ItemStack icon,
		Component label,
		boolean selected,
		boolean hovered,
		Runnable onClick
	) {
		int bg = selected ? 0xFF2A3A55 : hovered ? 0xFF222230 : 0xFF16161E;
		int border = selected ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_BORDER;
		PvDraw.fill(g, x, y, TAB, TAB, bg);
		g.outline(x, y, TAB, TAB, border);
		g.item(icon, x + 5, y + 5);
		this.hits.add(new Hit(x, y, TAB, TAB, onClick));
		if (hovered) {
			tooltipAbove(g, font, label, x + TAB / 2, y);
		}
	}

	/**
	 * Folder tabs along the top of the panel. Almost entirely above {@code panelY}.
	 */
	public void drawTopFrameTabs(
		GuiGraphicsExtractor g,
		Font font,
		int panelX,
		int panelY,
		int mouseX,
		int mouseY,
		List<Entry> entries,
		Object selectedKey
	) {
		// Tab body hangs above the panel; only SEAM dips onto the border.
		int tabY = panelY - TAB + SEAM;
		int cx = panelX + 4;
		for (Entry entry : entries) {
			boolean selected = entry.key.equals(selectedKey);
			boolean hovered = mouseX >= cx && mouseX < cx + TAB && mouseY >= tabY && mouseY < panelY + 1;
			drawTopTab(g, font, cx, tabY, panelY, entry.icon, entry.label, selected, hovered, entry.onClick);
			cx += TAB + GAP;
		}
	}

	/**
	 * Folder tabs along the left of the panel. Almost entirely left of {@code panelX}.
	 */
	public void drawLeftFrameTabs(
		GuiGraphicsExtractor g,
		Font font,
		int panelX,
		int panelY,
		int mouseX,
		int mouseY,
		List<Entry> entries,
		Object selectedKey
	) {
		int tabX = panelX - TAB + SEAM;
		int cy = panelY + 8;
		for (Entry entry : entries) {
			boolean selected = entry.key.equals(selectedKey);
			boolean hovered = mouseX >= tabX && mouseX < panelX + 1 && mouseY >= cy && mouseY < cy + TAB;
			drawLeftTab(g, font, tabX, cy, panelX, entry.icon, entry.label, selected, hovered, entry.onClick);
			cy += TAB + GAP;
		}
	}

	private void drawTopTab(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int panelY,
		ItemStack icon,
		Component label,
		boolean selected,
		boolean hovered,
		Runnable onClick
	) {
		int h = panelY - y + (selected ? SEAM : 0);
		if (h < TAB - SEAM) {
			h = TAB - SEAM;
		}
		int bg = selected ? PvDraw.COLOR_PANEL : hovered ? 0xF0202030 : 0xF012121C;
		int border = hovered && !selected ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_BORDER;

		PvDraw.fill(g, x, y, TAB, h, bg);
		// top + sides
		g.fill(x, y, x + TAB, y + 1, border);
		g.fill(x, y, x + 1, y + h, border);
		g.fill(x + TAB - 1, y, x + TAB, y + h, border);
		if (!selected) {
			// bottom sits on the panel rim
			g.fill(x, y + h - 1, x + TAB, y + h, border);
		} else {
			// erase panel top border under selected tab
			PvDraw.fill(g, x + 1, panelY, TAB - 2, 2, PvDraw.COLOR_PANEL);
		}

		g.item(icon, x + 5, y + 4);
		this.hits.add(new Hit(x, y, TAB, Math.max(h, TAB - SEAM), onClick));
		if (hovered) {
			tooltipAbove(g, font, label, x + TAB / 2, y);
		}
	}

	private void drawLeftTab(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int panelX,
		ItemStack icon,
		Component label,
		boolean selected,
		boolean hovered,
		Runnable onClick
	) {
		int w = panelX - x + (selected ? SEAM : 0);
		if (w < TAB - SEAM) {
			w = TAB - SEAM;
		}
		int bg = selected ? PvDraw.COLOR_PANEL : hovered ? 0xF0202030 : 0xF012121C;
		int border = hovered && !selected ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_BORDER;

		PvDraw.fill(g, x, y, w, TAB, bg);
		g.fill(x, y, x + w, y + 1, border);
		g.fill(x, y, x + 1, y + TAB, border);
		g.fill(x, y + TAB - 1, x + w, y + TAB, border);
		if (!selected) {
			g.fill(x + w - 1, y, x + w, y + TAB, border);
		} else {
			PvDraw.fill(g, panelX, y + 1, 2, TAB - 2, PvDraw.COLOR_PANEL);
		}

		g.item(icon, x + 4, y + 5);
		this.hits.add(new Hit(x, y, Math.max(w, TAB - SEAM), TAB, onClick));
		if (hovered) {
			tooltipLeft(g, font, label, x, y + TAB / 2);
		}
	}

	public void addHit(int x, int y, int w, int h, Runnable onClick) {
		this.hits.add(new Hit(x, y, w, h, onClick));
	}

	public void maybeTooltip(GuiGraphicsExtractor g, Font font, Component label, boolean hovered, int x, int y) {
		if (!hovered) {
			return;
		}
		int[] screen = screenSize();
		PvTooltip.drawComponents(g, font, List.of(label), x, y, screen[0], screen[1]);
	}

	private static void tooltipAbove(GuiGraphicsExtractor g, Font font, Component label, int cx, int topY) {
		int[] screen = screenSize();
		PvTooltip.drawCenteredAbove(g, font, label, cx, topY, screen[0], screen[1]);
	}

	private static void tooltipLeft(GuiGraphicsExtractor g, Font font, Component label, int leftX, int cy) {
		int[] screen = screenSize();
		PvTooltip.drawCenteredLeft(g, font, label, leftX, cy, screen[0], screen[1]);
	}

	private static int[] screenSize() {
		var window = Minecraft.getInstance().getWindow();
		return new int[] { window.getGuiScaledWidth(), window.getGuiScaledHeight() };
	}

	public record Entry(Object key, ItemStack icon, Component label, Runnable onClick) {
	}

	private record Hit(int x, int y, int w, int h, Runnable onClick) {
		boolean contains(double mx, double my) {
			return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
		}
	}
}
