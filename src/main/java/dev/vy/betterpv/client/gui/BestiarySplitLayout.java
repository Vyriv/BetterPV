package dev.vy.betterpv.client.gui;

import dev.vy.betterpv.client.data.BestiaryData;
import dev.vy.betterpv.client.data.FormatUtil;
import dev.vy.betterpv.client.gui.bestiary.BestiaryPage;
import dev.vy.betterpv.client.gui.nav.IconButtonBar;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Bestiary tab body split: left list + right category grid + search. */
final class BestiarySplitLayout {
	private Component bestiaryCategoryTip;
	private int bestiaryCategoryTipX;
	private int bestiaryCategoryTipY;

	Component categoryTip() {
		return this.bestiaryCategoryTip;
	}

	int categoryTipX() {
		return this.bestiaryCategoryTipX;
	}

	int categoryTipY() {
		return this.bestiaryCategoryTipY;
	}

	void render(
		GuiGraphicsExtractor g,
		Font font,
		BestiaryPage bestiaryPage,
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
		int searchH = InventorySplitLayout.SEARCH_H;
		int searchGap = InventorySplitLayout.SEARCH_GAP;

		PvDraw.fill(g, x, y, w, searchH, 0xFF101018);
		g.outline(x, y, w, searchH, PvDraw.COLOR_BORDER);
		String header = "Tiers "
			+ FormatUtil.commas(bestiaryPage.totalUnlockedTiers())
			+ "/"
			+ FormatUtil.commas(bestiaryPage.totalMaxTiers())
			+ "  ·  Milestone "
			+ FormatUtil.commas(bestiaryPage.milestone());
		PvDraw.text(g, font, header, x + 6, y + (searchH - font.lineHeight) / 2, PvDraw.COLOR_MUTED);

		int bodyY = y + searchH + searchGap;
		int bodyH = h - searchH - searchGap;

		BestiaryData.ensureLoaded();
		List<BestiaryData.Category> cats = BestiaryData.categories();
		int[] rowWidths = bestiaryRowWidths(cats.size());
		int btn = 22;
		int btnGap = 10;
		int pad = 8;
		int rows = rowWidths.length;
		int maxCols = 0;
		for (int width : rowWidths) {
			maxCols = Math.max(maxCols, width);
		}
		int gridW = maxCols <= 0 ? btn : maxCols * btn + (maxCols - 1) * btnGap;

		int gap = 8;
		int rightW = gridW + pad * 2;
		int leftW = w - rightW - gap;
		if (leftW < 160) {
			leftW = Math.max(140, (int) (w * 0.58));
			rightW = w - leftW - gap;
		}
		int rightX = x + leftW + gap;

		PvDraw.innerPanel(g, x, bodyY, leftW, bodyH);
		bestiaryPage.setSearchQuery(inventorySearchQuery);
		bestiaryPage.render(g, font, x, bodyY, leftW, bodyH, mouseX, mouseY, screenW, screenH);

		PvDraw.innerPanel(g, rightX, bodyY, rightW, bodyH);

		int startY = bodyY + pad;
		int index = 0;
		this.bestiaryCategoryTip = null;
		for (int row = 0; row < rows && index < cats.size(); row++) {
			int width = rowWidths[row];
			int rowW = width * btn + (width - 1) * btnGap;
			int rowStartX = rightX + (rightW - rowW) / 2;
			for (int col = 0; col < width && index < cats.size(); col++) {
				BestiaryData.Category cat = cats.get(index++);
				int bx = rowStartX + col * (btn + btnGap);
				int by = startY + row * (btn + btnGap);
				boolean selected = cat.id().equals(bestiaryPage.categoryId());
				boolean hovered = mouseX >= bx && mouseX < bx + btn && mouseY >= by && mouseY < by + btn;
				boolean searchHit = bestiaryPage.searchHighlightsCategory(cat.id());
				int bg = selected ? 0xFF2A3A55 : hovered ? 0xFF222230 : 0xFF16161E;
				int border = searchHit ? 0xFF55FF55 : selected ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_BORDER;
				PvDraw.fill(g, bx, by, btn, btn, bg);
				g.outline(bx, by, btn, btn, border);
				ItemStack icon = bestiaryPage.categoryIcon(cat.id());
				if (!icon.isEmpty()) {
					g.item(icon, bx + (btn - 16) / 2, by + (btn - 16) / 2);
				}
				String catId = cat.id();
				inventoryBar.addHit(bx, by, btn, btn, () -> bestiaryPage.setCategory(catId));
				if (hovered) {
					this.bestiaryCategoryTip = Component.literal(cat.name());
					this.bestiaryCategoryTipX = bx - 4;
					this.bestiaryCategoryTipY = by + btn / 2;
				}
			}
		}

		int searchBoxY = bodyY + bodyH - pad - searchH;
		int searchBoxX = rightX + pad;
		int searchBoxW = Math.max(20, rightW - pad * 2);
		PvDraw.fill(g, searchBoxX, searchBoxY, searchBoxW, searchH, 0xFF101018);
		g.outline(
			searchBoxX,
			searchBoxY,
			searchBoxW,
			searchH,
			inventorySearch != null && inventorySearch.isFocused() ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_BORDER
		);
		InventorySplitLayout.layoutInventorySearch(font, inventorySearch, searchBoxX, searchBoxY, searchBoxW);
		if (inventorySearch != null) {
			inventorySearch.extractWidgetRenderState(g, mouseX, mouseY, delta);
			if (inventorySearch.getValue().isEmpty()) {
				String hint = Component.translatable("betterpv.bestiary.search_hint").getString();
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

	private static int[] bestiaryRowWidths(int count) {
		if (count <= 0) {
			return new int[0];
		}
		if (count <= 5) {
			return new int[] { count };
		}
		if (count <= 10) {
			return new int[] { 5, count - 5 };
		}
		if (count <= 15) {
			return new int[] { 5, 5, count - 10 };
		}
		if (count <= 19) {
			return new int[] { 5, 5, 5, count - 15 };
		}
		return new int[] { 5, 5, 5, 5, Math.max(1, count - 20) };
	}
}
