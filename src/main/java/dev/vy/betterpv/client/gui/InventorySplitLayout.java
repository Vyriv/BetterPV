package dev.vy.betterpv.client.gui;

import dev.vy.betterpv.client.gui.inventories.InventoryPage;
import dev.vy.betterpv.client.gui.nav.IconButtonBar;
import dev.vy.betterpv.client.gui.nav.InventoryPane;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Inventory tab body split: left pane + right selector grid + search. */
final class InventorySplitLayout {
	static final int SEARCH_H = 16;
	static final int SEARCH_GAP = 4;
	private static final int INV_SELECTOR_COLS = 5;

	private int invSelectorX;
	private int invSelectorY;
	private int invSelectorW;
	private int invSelectorH;
	private int invSelectorScroll;
	private int invSelectorMaxScroll;
	private Component inventoryPaneTip;
	private int inventoryPaneTipX;
	private int inventoryPaneTipY;

	Component paneTip() {
		return this.inventoryPaneTip;
	}

	int paneTipX() {
		return this.inventoryPaneTipX;
	}

	int paneTipY() {
		return this.inventoryPaneTipY;
	}

	boolean hitSelector(double mouseX, double mouseY) {
		return mouseX >= this.invSelectorX && mouseX < this.invSelectorX + this.invSelectorW
			&& mouseY >= this.invSelectorY && mouseY < this.invSelectorY + this.invSelectorH;
	}

	boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		if (!hitSelector(mouseX, mouseY) || this.invSelectorMaxScroll <= 0) {
			return false;
		}
		int next = Math.max(0, Math.min(
			this.invSelectorMaxScroll,
			this.invSelectorScroll - (int) Math.round(scrollY * 18)
		));
		if (next != this.invSelectorScroll) {
			this.invSelectorScroll = next;
			return true;
		}
		return false;
	}

	void render(
		GuiGraphicsExtractor g,
		Font font,
		InventoryPage inventoryPage,
		IconButtonBar inventoryBar,
		EditBox inventorySearch,
		String inventorySearchQuery,
		int screenW,
		int screenH,
		int x,
		int y,
		int w,
		int h,
		int mouseX,
		int mouseY,
		float delta
	) {
		PvDraw.fill(g, x, y, w, SEARCH_H, 0xFF101018);
		g.outline(x, y, w, SEARCH_H, PvDraw.COLOR_BORDER);

		int bodyY = y + SEARCH_H + SEARCH_GAP;
		int bodyH = h - SEARCH_H - SEARCH_GAP;

		List<InventoryPane> panes = inventoryPage.visiblePanes();
		int[] rowWidths = inventoryRowWidths(panes.size());
		int btn = 22;
		int btnGap = 14;
		int pad = 10;
		int rows = rowWidths.length;
		// Keep the pre-Carnival selector width (5 columns) so extra icons wrap
		// inside the selector instead of shifting the inventory split.
		int maxCols = INV_SELECTOR_COLS;
		int gridW = maxCols * btn + (maxCols - 1) * btnGap;

		int gap = 8;
		int rightW = gridW + pad * 2;
		int leftW = w - rightW - gap;
		if (leftW < 150) {
			leftW = Math.max(130, (int) (w * 0.52));
			rightW = w - leftW - gap;
		}
		int rightX = x + leftW + gap;

		PvDraw.innerPanel(g, x, bodyY, leftW, bodyH);
		inventoryPage.setSearchQuery(inventorySearchQuery);
		inventoryPage.render(g, font, x, bodyY, leftW, bodyH, mouseX, mouseY, screenW, screenH);

		PvDraw.innerPanel(g, rightX, bodyY, rightW, bodyH);

		int startY = bodyY + pad;
		int searchBoxY = bodyY + bodyH - pad - SEARCH_H;
		int gridClipBottom = Math.max(startY + btn, searchBoxY - pad);
		int gridAreaH = Math.max(btn, gridClipBottom - startY);
		int gridContentH = rows <= 0 ? 0 : rows * btn + Math.max(0, rows - 1) * btnGap;
		this.invSelectorMaxScroll = Math.max(0, gridContentH - gridAreaH);
		this.invSelectorScroll = Math.max(0, Math.min(this.invSelectorScroll, this.invSelectorMaxScroll));
		this.invSelectorX = rightX;
		this.invSelectorY = startY;
		this.invSelectorW = rightW;
		this.invSelectorH = gridAreaH;

		int index = 0;
		this.inventoryPaneTip = null;
		g.enableScissor(rightX + 1, startY, rightX + rightW - 1, gridClipBottom);
		for (int row = 0; row < rows && index < panes.size(); row++) {
			int width = rowWidths[row];
			int rowW = width * btn + (width - 1) * btnGap;
			int rowStartX = rightX + (rightW - rowW) / 2;
			for (int col = 0; col < width && index < panes.size(); col++) {
				InventoryPane pane = panes.get(index++);
				int bx = rowStartX + col * (btn + btnGap);
				int by = startY + row * (btn + btnGap) - this.invSelectorScroll;
				if (by + btn <= startY || by >= gridClipBottom) {
					continue;
				}
				boolean selected = pane == inventoryPage.pane();
				boolean hovered = mouseX >= bx && mouseX < bx + btn
					&& mouseY >= Math.max(by, startY) && mouseY < Math.min(by + btn, gridClipBottom);
				boolean searchHit = inventoryPage.searchHighlightsPane(pane);
				int bg = selected ? 0xFF2A3A55 : hovered ? 0xFF222230 : 0xFF16161E;
				int border = searchHit ? 0xFF55FF55 : selected ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_BORDER;
				PvDraw.fill(g, bx, by, btn, btn, bg);
				g.outline(bx, by, btn, btn, border);
				Identifier textureIcon = pane.textureIcon();
				if (textureIcon != null) {
					int ix = bx + (btn - 16) / 2;
					int iy = by + (btn - 16) / 2;
					g.blit(
						RenderPipelines.GUI_TEXTURED,
						textureIcon,
						ix, iy,
						0, 0,
						16, 16,
						16, 16,
						16, 16
					);
				} else {
					g.item(pane.icon(), bx + (btn - 16) / 2, by + (btn - 16) / 2);
				}
				inventoryBar.addHit(bx, by, btn, btn, () -> inventoryPage.setPane(pane));
				if (hovered) {
					// Defer tip until after all buttons / search so later icons don't cover it.
					this.inventoryPaneTip = pane.label();
					this.inventoryPaneTipX = bx - 4;
					this.inventoryPaneTipY = by + btn / 2;
				}
			}
		}
		g.disableScissor();

		int searchBoxX = rightX + pad;
		int searchBoxW = rightW - pad * 2;
		PvDraw.fill(g, searchBoxX, searchBoxY, searchBoxW, SEARCH_H, 0xFF101018);
		g.outline(
			searchBoxX,
			searchBoxY,
			searchBoxW,
			SEARCH_H,
			inventorySearch != null && inventorySearch.isFocused() ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_BORDER
		);
		layoutInventorySearch(font, inventorySearch, searchBoxX, searchBoxY, searchBoxW);
		if (inventorySearch != null) {
			inventorySearch.extractWidgetRenderState(g, mouseX, mouseY, delta);
			// EditBox only draws its hint when unfocused; keep placeholder visible while empty.
			if (inventorySearch.getValue().isEmpty()) {
				String hint = Component.translatable("betterpv.inv.search_hint").getString();
				PvDraw.text(
					g,
					font,
					hint,
					inventorySearch.getX(),
					inventorySearch.getY(),
					PvDraw.COLOR_MUTED
				);
			}
		}
	}

	static void layoutInventorySearch(Font font, EditBox inventorySearch, int x, int y, int w) {
		if (inventorySearch == null) {
			return;
		}
		int inset = 4;
		// Unbordered EditBox draws text at getY() (not vertically centred) - match the box.
		int textH = Math.max(8, font.lineHeight);
		int textY = y + Math.max(0, (SEARCH_H - textH) / 2);
		inventorySearch.setWidth(Math.max(20, w - inset * 2));
		inventorySearch.setHeight(textH);
		inventorySearch.setX(x + inset);
		inventorySearch.setY(textY);
		inventorySearch.setVisible(true);
	}

	static void hideInventorySearch(EditBox inventorySearch) {
		if (inventorySearch == null) {
			return;
		}
		inventorySearch.setFocused(false);
		inventorySearch.setVisible(false);
	}

	private static int[] inventoryRowWidths(int count) {
		if (count <= 0) {
			return new int[0];
		}
		int rows = (count + INV_SELECTOR_COLS - 1) / INV_SELECTOR_COLS;
		int[] widths = new int[rows];
		int remaining = count;
		for (int i = 0; i < rows; i++) {
			widths[i] = Math.min(INV_SELECTOR_COLS, remaining);
			remaining -= widths[i];
		}
		return widths;
	}
}
