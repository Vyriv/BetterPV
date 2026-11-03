package dev.vy.betterpv.client.gui.fishing;

import dev.vy.betterpv.client.data.FishingSnapshot;
import dev.vy.betterpv.client.data.FormatUtil;
import dev.vy.betterpv.client.data.TrophyFishData;
import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.inventories.SkyBlockItemFactory;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Trial layout: trophy fish and trophy frogs on one panel as named entries with tier pips.
 * Space is split by row count, so the short frog list only takes what it needs.
 */
public final class FishingTrophyTrialPage {
	private static final int PAD = 6;
	private static final int SLOT = 16;
	private static final int CELL_MIN_W = 96;
	private static final int MAX_COLS = 5;
	private static final int COL_GAP = 6;
	private static final int CELL_H_MAX = 22;
	private static final int CELL_H_MIN = 17;
	private static final int SECTION_GAP = 6;
	private static final int PIP = 4;
	private static final int PIP_GAP = 2;
	private static final int ITEM_SLOT_BG = 0xFF101018;
	private static final TrophyFishData.Tier[] PIP_ORDER = {
		TrophyFishData.Tier.BRONZE,
		TrophyFishData.Tier.SILVER,
		TrophyFishData.Tier.GOLD,
		TrophyFishData.Tier.DIAMOND
	};

	private record Zone(int x, int y, int w, int h, FishingSnapshot.TrophyRow row) {
	}

	private final List<Zone> zones = new ArrayList<>();
	private int clipBottom = Integer.MAX_VALUE;

	public void render(
		GuiGraphicsExtractor g, Font font, FishingSnapshot snapshot,
		int x, int y, int w, int h
	) {
		this.zones.clear();
		FishingSnapshot data = snapshot == null ? FishingSnapshot.empty() : snapshot;
		List<FishingSnapshot.TrophyRow> fish = FishingPage.sortedRows(data.trophyFish());
		List<FishingSnapshot.TrophyRow> frogs = FishingPage.sortedRows(data.trophyFrogs());

		PvDraw.innerPanel(g, x, y, w, h);
		if (fish.isEmpty() && frogs.isEmpty()) {
			PvDraw.textCentered(g, font, "No data", x + w / 2, y + h / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED);
			return;
		}

		int innerX = x + PAD;
		int innerW = w - PAD * 2;
		int top = y + PAD;
		int bottom = y + h - PAD;
		int cols = Math.max(1, Math.min(MAX_COLS, (innerW + COL_GAP) / (CELL_MIN_W + COL_GAP)));
		int cellW = (innerW - COL_GAP * (cols - 1)) / cols;

		int headerH = headerHeight(font);
		int sections = frogs.isEmpty() ? 1 : 2;
		int gridRows = rowsFor(fish.size(), cols) + rowsFor(frogs.size(), cols);
		int avail = bottom - top - sections * headerH - (sections - 1) * SECTION_GAP;
		int cellH = gridRows <= 0 ? CELL_H_MAX : Math.max(CELL_H_MIN, Math.min(CELL_H_MAX, avail / gridRows));

		this.clipBottom = y + h - 1;
		g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
		String last = data.trophyFishLastCaught().isBlank()
			? null
			: "Last: " + FishingPage.prettyLast(data.trophyFishLastCaught());
		int cy = drawSection(g, font, "Trophy Fish", data.trophyFishTotal(), last, fish,
			x, w, innerX, top, innerW, cols, cellW, cellH);
		if (!frogs.isEmpty()) {
			cy += SECTION_GAP;
			drawSection(g, font, "Trophy Frogs", data.itemsFishedTrophyFrog(), null, frogs,
				x, w, innerX, cy, innerW, cols, cellW, cellH);
		}
		g.disableScissor();
	}

	public void renderTooltip(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int screenW, int screenH) {
		for (Zone zone : this.zones) {
			if (mouseX >= zone.x() && mouseX < zone.x() + zone.w() && mouseY >= zone.y() && mouseY < zone.y() + zone.h()) {
				FishingPage.drawTrophyStatTooltip(g, font, zone.row(), mouseX, mouseY, screenW, screenH);
				return;
			}
		}
	}

	private int drawSection(
		GuiGraphicsExtractor g, Font font, String title, long total, String subtitle,
		List<FishingSnapshot.TrophyRow> rows,
		int panelX, int panelW, int x, int y, int w, int cols, int cellW, int cellH
	) {
		PvDraw.text(g, font, title, x, y, PvDraw.COLOR_MUTED);
		PvDraw.textRight(g, font, FormatUtil.commas(total) + " caught", x + w, y, PvDraw.COLOR_ACCENT);
		int ly = y + font.lineHeight + 2;

		int tx = x;
		for (int i = PIP_ORDER.length - 1; i >= 0; i--) {
			TrophyFishData.Tier tier = PIP_ORDER[i];
			int have = 0;
			for (FishingSnapshot.TrophyRow row : rows) {
				if (row.counts().discovered(tier)) {
					have++;
				}
			}
			String part = FishingPage.tierTitle(tier) + " " + have + "/" + rows.size();
			PvDraw.text(g, font, part, tx, ly, FishingPage.tierColor(tier));
			tx += font.width(part) + 8;
		}
		if (subtitle != null) {
			int room = x + w - tx;
			if (room > 40) {
				String shown = FishingPage.trim(font, subtitle, room);
				PvDraw.textRight(g, font, shown, x + w, ly, PvDraw.COLOR_MUTED);
			}
		}
		ly += font.lineHeight + 2;
		int lineInset = PAD + 4;
		PvDraw.fill(g, panelX + lineInset, ly + 1, Math.max(0, panelW - lineInset * 2), 1, 0x33FFFFFF);
		ly += 5;

		for (int i = 0; i < rows.size(); i++) {
			int cx = x + (i % cols) * (cellW + COL_GAP);
			int cy = ly + (i / cols) * cellH;
			drawCell(g, font, rows.get(i), cx, cy, cellW, cellH);
		}
		return ly + rowsFor(rows.size(), cols) * cellH;
	}

	private void drawCell(GuiGraphicsExtractor g, Font font, FishingSnapshot.TrophyRow row, int x, int y, int w, int h) {
		int slotY = y + Math.max(0, (h - SLOT) / 2);
		PvDraw.fill(g, x, slotY, SLOT, SLOT, ITEM_SLOT_BG);
		TrophyFishData.Tier best = row.counts().bestDiscovered();
		if (best != null) {
			ItemStack skull = SkyBlockItemFactory.trophySkullStack(row.id() + "_" + best.suffix());
			if (skull.isEmpty()) {
				skull = new ItemStack(Items.TROPICAL_FISH);
			}
			g.item(skull, x, slotY);
			FishingPage.drawTierBorder(g, x, slotY, FishingPage.tierColor(best));
		} else {
			g.item(new ItemStack(Items.DYE.gray()), x, slotY);
		}

		int textX = x + SLOT + 4;
		int blockH = font.lineHeight + 2 + PIP;
		int nameY = y + Math.max(0, (h - blockH) / 2);
		String total = row.countsExact() && row.totalCaught() > 0 ? FormatUtil.commas(row.totalCaught()) : "";
		int totalW = total.isEmpty() ? 0 : font.width(total);
		int nameMax = Math.max(8, x + w - textX - (totalW > 0 ? totalW + 4 : 0));
		String name = FishingPage.trim(font, row.name() == null ? "" : row.name(), nameMax);
		PvDraw.text(g, font, name, textX, nameY, row.anyDiscovered() ? PvDraw.COLOR_TEXT : PvDraw.COLOR_MUTED);
		if (!total.isEmpty()) {
			PvDraw.textRight(g, font, total, x + w, nameY, PvDraw.COLOR_MUTED);
		}

		int pipY = nameY + font.lineHeight + 2;
		for (int i = 0; i < PIP_ORDER.length; i++) {
			TrophyFishData.Tier tier = PIP_ORDER[i];
			int color = row.counts().discovered(tier) ? FishingPage.tierColor(tier) : PvDraw.COLOR_BAR_BG;
			PvDraw.fill(g, textX + i * (PIP + PIP_GAP), pipY, PIP, PIP, color);
		}
		if (y + h <= this.clipBottom) {
			this.zones.add(new Zone(x, y, w, h, row));
		}
	}

	private static int headerHeight(Font font) {
		return (font.lineHeight + 2) * 2 + 5;
	}

	private static int rowsFor(int count, int cols) {
		return count <= 0 ? 0 : (count + cols - 1) / cols;
	}
}
