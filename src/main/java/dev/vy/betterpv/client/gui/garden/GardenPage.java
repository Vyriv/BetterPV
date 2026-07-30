package dev.vy.betterpv.client.gui.garden;

import dev.vy.betterpv.client.data.FormatUtil;
import dev.vy.betterpv.client.data.GardenData;
import dev.vy.betterpv.client.data.GardenSnapshot;
import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.PvTooltip;
import dev.vy.betterpv.client.gui.inventories.SkyBlockItemFactory;
import dev.vy.betterpv.client.gui.nav.PvSubTab;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Garden page: Overview / Visitors / Crops / Composter / Greenhouse / Jacob. */
public final class GardenPage {
	private static final int PAD = 6;
	private static final int GAP = 6;
	private static final int BAR_LABEL_GAP = 2;
	private static final int BAR_AFTER = 4;
	private static final int STAT_ROW = 12;
	private static final int ICON = 16;
	private static final int SLOT = 20;
	private static final int SLOT_GAP = 4;
	private static final int ITEM_SLOT_BG = 0xFF101018;
	private static final int ITEM_SLOT_BORDER = 0xFF2A2A35;
	private static final int ITEM_SLOT_HOVER = 0xFF4A4A5A;
	private static final int VISITOR_CELL_W = 22;
	private static final int VISITOR_CELL_H = 40;
	private static final int CHIP_CELL = 22;
	private static final int CHIP_GAP = 4;
	private static final int GH_CELL_W = 24;
	private static final int GH_CELL_H = 42;
	private static final int CELL_BORDER = 0xFF3A3A48;
	private static final int CREDIT_C = 0xFF4A4A58;
	private static final int BAR_CROP = 0xFF6BAA3D;
	private static final int BAR_GARDEN = 0xFF4CAF50;
	private static final int BAR_FARM = 0xFF8BC34A;
	private static final int BAR_VISITOR = 0xFF9C6B4A;
	private static final int BAR_COMPOST = 0xFF7CB342;
	private static final int BRONZE = 0xFFCD7F32;
	private static final int SILVER = 0xFFC0C0C0;
	private static final int GOLD = 0xFFFFD700;
	private static final int PLATINUM = 0xFF55FFFF;
	private static final int DIAMOND = 0xFFAAFFFF;
	private static final int MEDAL_ORB_EMPTY = 0xFF2A2A35;
	private static final int GHOST = 0xFF9A9AAC;
	private static final int COPPER = 0xFFE07A3D;
	private static final int VISITS_C = 0xFFE8E8F0;
	private static final int COMPLETED_C = 0xFF55FF55;
	private static final int REJECTED_C = 0xFFFF5555;
	private static final float CREDIT_SCALE = 0.75F;
	private static final int FLIP_MS = 480;
	private static final int PANEL_HOVER = 0x0AFFFFFF;
	private static final int ORB = 6;
	private static final int ORB_GAP = 3;

	private GardenSnapshot snapshot = GardenSnapshot.empty();
	private PvSubTab lastSub;
	private int scroll;
	private int maxScroll;
	private int scrollTop;
	private int scrollH;
	private int leftScroll;
	private int leftMaxScroll;
	private int leftScrollTop;
	private int leftScrollH;
	private int leftScrollX;
	private int leftScrollW;
	private final List<HoverZone> zones = new ArrayList<>();
	private boolean jacobExtrasFace;
	private boolean jacobFlipTarget;
	private long jacobFlipStartMs;
	private int jacobHitX;
	private int jacobHitY;
	private int jacobHitW;
	private int jacobHitH;

	public void apply(GardenSnapshot snapshot) {
		this.snapshot = snapshot == null ? GardenSnapshot.empty() : snapshot;
		this.scroll = 0;
		this.leftScroll = 0;
		this.jacobExtrasFace = false;
		this.jacobFlipTarget = false;
		this.jacobFlipStartMs = 0L;
		this.zones.clear();
		prefetch();
	}

	/** Update data without resetting scroll (async weight / contests). */
	public void patch(GardenSnapshot snapshot) {
		this.snapshot = snapshot == null ? GardenSnapshot.empty() : snapshot;
		prefetch();
	}

	public GardenSnapshot snapshot() {
		return this.snapshot;
	}

	public void render(
		GuiGraphicsExtractor g, Font font, PvSubTab sub,
		int x, int y, int w, int h, int mouseX, int mouseY, int screenW, int screenH
	) {
		this.zones.clear();
		PvSubTab mode = sub == null ? PvSubTab.GARDEN_OVERVIEW : sub;
		if (mode != this.lastSub) {
			this.lastSub = mode;
			this.scroll = 0;
			this.leftScroll = 0;
		}

		boolean islandReady = this.snapshot.islandLoaded();
		boolean islandBusy = this.snapshot.islandLoading() || (!islandReady && this.snapshot.islandError().isBlank());
		boolean needsIsland = mode != PvSubTab.GARDEN_JACOB && mode != PvSubTab.GARDEN_GREENHOUSE;

		if (!islandReady && needsIsland) {
			if (mode == PvSubTab.GARDEN_OVERVIEW) {
				renderOverview(g, font, x, y, w, h, mouseX, mouseY, true);
			} else {
				this.maxScroll = 0;
				this.leftMaxScroll = 0;
				PvDraw.innerPanel(g, x, y, w, h);
				String msg = islandBusy
					? "Loading garden..."
					: (this.snapshot.islandError().isBlank() ? "Garden unavailable" : this.snapshot.islandError());
				PvDraw.textCentered(g, font, msg, x + w / 2, y + h / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED);
			}
			drawHover(g, font, mouseX, mouseY, screenW, screenH);
			return;
		}

		switch (mode) {
			case GARDEN_VISITORS -> renderVisitors(g, font, x, y, w, h, mouseX, mouseY);
			case GARDEN_CROPS, GARDEN_COMPOSTER -> renderCropsAndComposter(g, font, x, y, w, h, mouseX, mouseY);
			case GARDEN_GREENHOUSE -> renderGreenhouse(g, font, x, y, w, h, mouseX, mouseY);
			case GARDEN_JACOB -> renderJacob(g, font, x, y, w, h, mouseX, mouseY);
			default -> renderOverview(g, font, x, y, w, h, mouseX, mouseY, false);
		}
		drawHover(g, font, mouseX, mouseY, screenW, screenH);
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY, PvSubTab sub) {
		boolean overLeft = this.leftMaxScroll > 0
			&& mouseX >= this.leftScrollX && mouseX < this.leftScrollX + this.leftScrollW
			&& mouseY >= this.leftScrollTop && mouseY < this.leftScrollTop + this.leftScrollH;
		if (overLeft) {
			int next = Math.max(0, Math.min(this.leftMaxScroll, this.leftScroll + (scrollY > 0 ? -14 : 14)));
			if (next != this.leftScroll) {
				this.leftScroll = next;
				return true;
			}
			return false;
		}
		if (this.maxScroll <= 0) {
			return false;
		}
		if (mouseY < this.scrollTop || mouseY >= this.scrollTop + this.scrollH) {
			return false;
		}
		int next = Math.max(0, Math.min(this.maxScroll, this.scroll + (scrollY > 0 ? -14 : 14)));
		if (next == this.scroll) {
			return false;
		}
		this.scroll = next;
		return true;
	}

	public boolean mouseClicked(double mx, double my) {
		if (this.lastSub != PvSubTab.GARDEN_JACOB) {
			return false;
		}
		if (mx < this.jacobHitX || mx >= this.jacobHitX + this.jacobHitW
			|| my < this.jacobHitY || my >= this.jacobHitY + this.jacobHitH) {
			return false;
		}
		if (this.jacobFlipStartMs != 0L) {
			return true;
		}
		this.jacobFlipTarget = !this.jacobExtrasFace;
		this.jacobFlipStartMs = System.currentTimeMillis();
		return true;
	}

	private void renderOverview(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my, boolean partial
	) {
		int rightW = Math.max(200, w * 55 / 100);
		int leftW = w - rightW - GAP;
		PvDraw.innerPanel(g, x, y, leftW, h);
		PvDraw.innerPanel(g, x + leftW + GAP, y, rightW, h);

		int lx = x + PAD;
		int ly = y + PAD;
		int lw = leftW - PAD * 2;
		int rowH = barRowH(font);

		ly = drawBar(g, font, "Garden", String.valueOf(this.snapshot.gardenLevel()),
			this.snapshot.gardenFill(), this.snapshot.gardenMaxed(), BAR_GARDEN,
			this.snapshot.gardenHover(), lx, ly, lw, mx, my) + BAR_AFTER;
		ly = drawBar(g, font, "Farming", String.valueOf(this.snapshot.farmingLevel()),
			this.snapshot.farmingFill(), this.snapshot.farmingMaxed(), BAR_FARM,
			this.snapshot.farmingHover(), lx, ly, lw, mx, my) + BAR_AFTER + 2;

		ly = statLine(g, font, "Plots",
			this.snapshot.islandLoaded()
				? this.snapshot.plotsUnlocked() + " / " + this.snapshot.plotsMax()
				: "...",
			lx, ly, lw, PvDraw.COLOR_TEXT) + 1;
		ly = statLine(g, font, "Copper", FormatUtil.commas(this.snapshot.copper()), lx, ly, lw, COPPER) + 2;

		GardenSnapshot.FarmingWeightInfo weight = this.snapshot.farmingWeight();
		String weightValue = weight.loading() ? "..."
			: weight.loaded() ? FormatUtil.oneDecimal(weight.displayTotal()) : "-";
		ly = statLine(g, font, "Farming weight", weightValue, lx, ly, lw, PvDraw.COLOR_GOLD) + 1;
		if (weight.loaded()) {
			this.zones.add(new HoverZone(lx, ly - STAT_ROW, lw, STAT_ROW, farmingWeightHover(weight)));
			ly += 2;
		} else if (!weight.error().isBlank()) {
			ly = statLine(g, font, "Weight", trim(font, weight.error(), lw / 2), lx, ly, lw, PvDraw.COLOR_MUTED) + 3;
		} else {
			ly += 2;
		}

		if (!this.snapshot.islandLoaded() && partial) {
			PvDraw.text(g, font, trim(font, this.snapshot.islandLoading() || this.snapshot.islandError().isBlank()
				? "Loading island..." : this.snapshot.islandError(), lw), lx, ly, PvDraw.COLOR_MUTED);
			ly += font.lineHeight + 4;
		}

		List<GardenSnapshot.ChipEntry> chips = this.snapshot.gardenChips();
		if (!chips.isEmpty()) {
			ly += 8;
			PvDraw.text(g, font, "Garden chips", lx, ly, PvDraw.COLOR_MUTED);
			ly += font.lineHeight + 3;
			int cols = Math.min(5, Math.max(4, (lw + CHIP_GAP) / (CHIP_CELL + CHIP_GAP)));
			int cellH = CHIP_CELL + font.lineHeight + 2;
			int gridW = cols * CHIP_CELL + (cols - 1) * CHIP_GAP;
			int gridX = lx + Math.max(0, (lw - gridW) / 2);
			int bottom = y + h - PAD;
			for (int i = 0; i < chips.size(); i++) {
				int col = i % cols;
				int row = i / cols;
				int bx = gridX + col * (CHIP_CELL + CHIP_GAP);
				int by = ly + row * (cellH + CHIP_GAP);
				if (by + cellH > bottom) {
					break;
				}
				GardenSnapshot.ChipEntry chip = chips.get(i);
				drawCellBorder(g, bx, by, CHIP_CELL, CHIP_CELL);
				int iconX = bx + (CHIP_CELL - ICON) / 2;
				int iconY = by + (CHIP_CELL - ICON) / 2;
				drawIcon(g, chip.iconId(), iconX, iconY, ICON, chipPackModel(chip.id()));
				String lvl = String.valueOf(chip.level());
				PvDraw.text(g, font, lvl, bx + (CHIP_CELL - font.width(lvl)) / 2, by + CHIP_CELL + 1, PvDraw.COLOR_ACCENT);
				this.zones.add(new HoverZone(bx, by, CHIP_CELL, cellH, List.of(
					PvTooltip.Line.of(chip.name(), PvDraw.COLOR_TEXT),
					PvTooltip.Line.of("Level " + chip.level(), PvDraw.COLOR_ACCENT)
				)));
			}
		}

		int rx = x + leftW + GAP + PAD;
		int ry = y + PAD;
		int rw = rightW - PAD * 2;
		PvDraw.text(g, font, "Crop milestones", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 4;

		if (!this.snapshot.islandLoaded()) {
			PvDraw.textCentered(g, font, partial ? "..." : "No crop data",
				x + leftW + GAP + rightW / 2, y + h / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED);
			this.maxScroll = 0;
			return;
		}

		List<GardenSnapshot.CropRow> crops = this.snapshot.crops();
		int cols = 2;
		int iconGap = 3;
		int colW = (rw - GAP) / cols;
		int barW = Math.max(40, colW - ICON - iconGap);
		int gridTop = ry;
		int gridH = y + h - PAD - gridTop;
		this.scrollTop = gridTop;
		this.scrollH = gridH;
		this.maxScroll = Math.max(0, ((crops.size() + 1) / cols) * rowH - gridH);
		this.scroll = Math.min(this.scroll, this.maxScroll);

		g.enableScissor(rx, gridTop, rx + rw, gridTop + gridH);
		int yy = gridTop - this.scroll;
		for (int i = 0; i < crops.size(); i++) {
			int col = i % cols;
			int row = i / cols;
			int bx = rx + col * (colW + GAP);
			int by = yy + row * rowH;
			if (by + rowH < gridTop || by > gridTop + gridH) {
				continue;
			}
			GardenSnapshot.CropRow crop = crops.get(i);
			int iconY = by + Math.max(0, (font.lineHeight + BAR_LABEL_GAP + PvDraw.BAR_HEIGHT - ICON) / 2);
			drawIcon(g, crop.iconId(), bx, iconY, ICON, GardenData.cropPackModel(crop.id()));
			drawBar(g, font, crop.name(), String.valueOf(crop.milestone()), crop.milestoneFill(),
				crop.milestoneMaxed(), BAR_CROP, null, bx + ICON + iconGap, by, barW, mx, my);
			int barBottom = by + font.lineHeight + BAR_LABEL_GAP + PvDraw.BAR_HEIGHT;
			this.zones.add(new HoverZone(bx, by, colW, Math.max(rowH, barBottom - by), cropMilestoneTip(crop)));
		}
		g.disableScissor();
	}

	private void renderVisitors(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my) {
		int rightW = Math.max(140, w * 30 / 100);
		int leftW = w - rightW - GAP;
		PvDraw.innerPanel(g, x, y, leftW, h);
		PvDraw.innerPanel(g, x + leftW + GAP, y, rightW, h);

		int lx = x + PAD;
		int ly = y + PAD;
		int lw = leftW - PAD * 2;

		List<GardenSnapshot.VisitorRow> list = this.snapshot.visitors();
		int cols = Math.max(1, (lw + SLOT_GAP) / (VISITOR_CELL_W + SLOT_GAP));
		int cellW = VISITOR_CELL_W;
		int cellH = VISITOR_CELL_H;
		int rows = (list.size() + cols - 1) / cols;
		int gridTop = ly;
		int gridH = y + h - PAD - gridTop;
		this.scrollTop = gridTop;
		this.scrollH = gridH;
		this.maxScroll = Math.max(0, rows * (cellH + SLOT_GAP) - gridH);
		this.scroll = Math.min(this.scroll, this.maxScroll);

		ItemStack grey = new ItemStack(Items.GRAY_DYE);
		ItemStack green = new ItemStack(Items.LIME_DYE);

		g.enableScissor(lx, gridTop, lx + lw, gridTop + gridH);
		int baseY = gridTop - this.scroll;
		for (int i = 0; i < list.size(); i++) {
			int col = i % cols;
			int row = i / cols;
			int bx = lx + col * (cellW + SLOT_GAP);
			int by = baseY + row * (cellH + SLOT_GAP);
			if (by + cellH < gridTop || by > gridTop + gridH) {
				continue;
			}
			GardenSnapshot.VisitorRow v = list.get(i);
			drawCellBorder(g, bx, by, cellW, cellH);
			int iconX = bx + (cellW - ICON) / 2;
			drawIcon(g, v.npcItemId(), iconX, by + 2, ICON, null);
			ItemStack dye = v.visited() ? green : grey;
			g.item(dye, iconX, by + 2 + ICON + 2);
			String rarity = GardenData.visitorRarity(v.id());
			List<PvTooltip.Line> tip = new ArrayList<>();
			tip.add(PvTooltip.Line.of(v.name(), PvDraw.COLOR_TEXT));
			if (!rarity.isBlank()) {
				tip.add(PvTooltip.Line.of(GardenData.prettyVisitorRarity(rarity), rarityColor(rarity)));
			}
			tip.add(PvTooltip.Line.of("Visits: " + FormatUtil.commas(v.visits()), VISITS_C));
			tip.add(PvTooltip.Line.of("Completed: " + FormatUtil.commas(v.completed()), COMPLETED_C));
			tip.add(PvTooltip.Line.of("Rejected: " + FormatUtil.commas(v.rejected()), REJECTED_C));
			this.zones.add(new HoverZone(bx, by, cellW, cellH, tip));
		}
		g.disableScissor();

		int rx = x + leftW + GAP + PAD;
		int ry = y + PAD;
		int rw = rightW - PAD * 2;
		ry = drawBar(g, font, "Visitors", String.valueOf(this.snapshot.visitorMilestone()),
			this.snapshot.visitorMilestoneFill(), this.snapshot.visitorMilestoneMaxed(), BAR_VISITOR,
			this.snapshot.visitorMilestoneHover(), rx, ry, rw, mx, my) + BAR_AFTER + 2;
		ry = statLine(g, font, "Offers accepted", FormatUtil.commas(this.snapshot.visitorsCompleted()),
			rx, ry, rw, COMPLETED_C) + 1;
		ry = statLine(g, font, "Offers declined", FormatUtil.commas(this.snapshot.totalRejected()),
			rx, ry, rw, REJECTED_C) + 1;
		ry = statLine(g, font, "Total visits", FormatUtil.commas(this.snapshot.totalVisits()),
			rx, ry, rw, VISITS_C) + 1;
		ry = statLine(g, font, "Unique Visitors", FormatUtil.commas(this.snapshot.uniqueVisitors()),
			rx, ry, rw, PvDraw.COLOR_MUTED) + 1;
		ry += STAT_ROW + 1;
		ry += STAT_ROW + 1;
		PvDraw.text(g, font, "Accepted Rarities", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 3;
		Map<String, Long> accepted = acceptedRarityTotals(this.snapshot.visitors());
		for (String rarity : GardenData.visitorRarityOrder()) {
			long count = accepted.getOrDefault(rarity, 0L);
			if (count <= 0L) {
				continue;
			}
			ry = statLine(g, font, GardenData.prettyVisitorRarity(rarity), FormatUtil.commas(count),
				rx, ry, rw, rarityColor(rarity)) + 1;
		}
		this.leftMaxScroll = 0;
	}

	/** Sum of completed offers per rarity (not unique visitors). */
	private static Map<String, Long> acceptedRarityTotals(List<GardenSnapshot.VisitorRow> visitors) {
		Map<String, Long> out = new LinkedHashMap<>();
		for (GardenSnapshot.VisitorRow v : visitors) {
			if (v.completed() <= 0L) {
				continue;
			}
			String rarity = GardenData.visitorRarity(v.id());
			if (rarity.isBlank()) {
				continue;
			}
			out.merge(rarity.toUpperCase(Locale.ROOT), v.completed(), Long::sum);
		}
		return out;
	}

	private void renderCropsAndComposter(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my) {
		int leftW = Math.max(140, w * 40 / 100);
		int rightW = w - leftW - GAP;
		PvDraw.innerPanel(g, x, y, leftW, h);
		PvDraw.innerPanel(g, x + leftW + GAP, y, rightW, h);

		int lx = x + PAD;
		int ly = y + PAD;
		int lw = leftW - PAD * 2;

		List<GardenSnapshot.CropRow> crops = this.snapshot.crops();
		PvDraw.text(g, font, "Crop milestones", lx, ly, PvDraw.COLOR_MUTED);
		if (!crops.isEmpty()) {
			long sum = 0L;
			for (GardenSnapshot.CropRow crop : crops) {
				sum += crop.milestone();
			}
			String avg = "Average: " + FormatUtil.oneDecimal(sum / (double) crops.size());
			if (font.width("Crop milestones") + 10 + font.width(avg) <= lw) {
				PvDraw.textRight(g, font, avg, lx + lw, ly, PvDraw.COLOR_MUTED);
			}
		}
		ly += font.lineHeight + 4;

		// Readable 2-col Overview-style rows; last odd item centered; row gap fills panel height.
		int cols = 2;
		int colGap = GAP;
		int colW = (lw - colGap) / cols;
		int miniBarH = 3;
		int labelGap = 2;
		int blockH = font.lineHeight + labelGap + miniBarH;
		int minRowH = Math.max(ICON, blockH) + 2;
		int gridRows = crops.isEmpty() ? 0 : (crops.size() + cols - 1) / cols;
		int gridTop = ly;
		int gridH = y + h - PAD - gridTop;
		int rowH = gridRows <= 0 ? minRowH : Math.max(minRowH, gridH / gridRows);
		this.scrollTop = gridTop;
		this.scrollH = gridH;
		this.maxScroll = 0;
		this.scroll = 0;
		this.leftMaxScroll = 0;

		g.enableScissor(lx, gridTop, lx + lw, gridTop + gridH);
		int lastRow = Math.max(0, gridRows - 1);
		int lastRowCount = crops.isEmpty() ? 0 : ((crops.size() - 1) % cols) + 1;
		for (int i = 0; i < crops.size(); i++) {
			int col = i % cols;
			int row = i / cols;
			int rowItems = row == lastRow ? lastRowCount : cols;
			int rowW = rowItems * colW + Math.max(0, rowItems - 1) * colGap;
			int rowX = lx + Math.max(0, (lw - rowW) / 2);
			int bx = rowX + col * (colW + colGap);
			int by = gridTop + row * rowH;
			if (by + rowH < gridTop || by > gridTop + gridH) {
				continue;
			}
			GardenSnapshot.CropRow crop = crops.get(i);
			int textX = bx + ICON + 3;
			int barW = Math.max(24, colW - ICON - 3);
			int iconY = by + Math.max(0, (blockH - ICON) / 2);
			drawIcon(g, crop.iconId(), bx, iconY, ICON, GardenData.cropPackModel(crop.id()));
			String name = crop.name();
			String lvl = String.valueOf(crop.milestone());
			int nameMax = Math.max(8, barW - font.width(lvl) - 4);
			PvDraw.text(g, font, trim(font, name, nameMax), textX, by, PvDraw.COLOR_TEXT);
			PvDraw.textRight(g, font, lvl, textX + barW, by, crop.milestoneMaxed() ? COMPLETED_C : PvDraw.COLOR_MUTED);
			PvDraw.progressBar(g, textX, by + font.lineHeight + labelGap, barW, miniBarH,
				crop.milestoneFill(), BAR_CROP, crop.milestoneMaxed());
			this.zones.add(new HoverZone(bx, by, colW, blockH + 1, cropMilestoneTip(crop)));
		}
		g.disableScissor();

		GardenSnapshot.Composter c = this.snapshot.composter();
		int rx = x + leftW + GAP + PAD;
		int ry = y + PAD;
		int rw = rightW - PAD * 2;
		PvDraw.text(g, font, "Composter", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 4;

		int cColGap = 8;
		int cColW = (rw - cColGap) / 2;
		int leftCol = rx;
		int rightCol = rx + cColW + cColGap;
		int row1 = ry;
		int row2 = ry + STAT_ROW + 1;
		statLine(g, font, "Organic Matter", FormatUtil.shortXp(c.organicMatter()), leftCol, row1, cColW, PvDraw.COLOR_TEXT);
		statLine(g, font, "Fuel", FormatUtil.shortXp(c.fuelUnits()), leftCol, row2, cColW, PvDraw.COLOR_TEXT);
		statLine(g, font, "Compost", FormatUtil.shortXp(c.compostUnits()), rightCol, row1, cColW, BAR_COMPOST);
		statLine(g, font, "Items", FormatUtil.shortXp(c.compostItems()), rightCol, row2, cColW, PvDraw.COLOR_MUTED);
		ry = row2 + STAT_ROW + 4;

		PvDraw.fill(g, rx, ry, rw, 1, PvDraw.COLOR_BORDER);
		ry += 5;

		PvDraw.text(g, font, "Upgrades", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 4;

		List<GardenSnapshot.ComposterUpgrade> upgrades = c.upgrades();
		int upBarH = 3;
		int upLabelGap = 2;
		int upBlockH = font.lineHeight + upLabelGap + upBarH;
		int upRowH = Math.max(ICON, upBlockH) + 3;
		int availH = Math.max(upRowH, y + h - PAD - ry);
		if (!upgrades.isEmpty()) {
			upRowH = Math.max(Math.max(ICON, upBlockH) + 1, availH / upgrades.size());
		}
		for (int i = 0; i < upgrades.size(); i++) {
			int by = ry + i * upRowH;
			GardenSnapshot.ComposterUpgrade u = upgrades.get(i);
			int textX = rx + ICON + 4;
			int barW = Math.max(20, rw - ICON - 4);
			int iconY = by + Math.max(0, (upBlockH - ICON) / 2);
			drawIcon(g, u.iconId(), rx, iconY, ICON, GardenData.composterUpgradePackModel(u.id()));
			String name = shortComposterName(u);
			String lvl = u.level() + " / " + u.maxLevel();
			int nameMax = Math.max(8, barW - font.width(lvl) - 4);
			PvDraw.text(g, font, trim(font, name, nameMax), textX, by, PvDraw.COLOR_TEXT);
			PvDraw.textRight(g, font, lvl, textX + barW, by, u.maxed() ? COMPLETED_C : PvDraw.COLOR_MUTED);
			PvDraw.progressBar(g, textX, by + font.lineHeight + upLabelGap, barW, upBarH, u.fill(), BAR_COMPOST, u.maxed());
			this.zones.add(new HoverZone(rx, by, rw, upBlockH + 1, List.of(
				PvTooltip.Line.of(u.name(), PvDraw.COLOR_TEXT),
				PvTooltip.Line.of("Purchased: " + u.level(), COMPLETED_C),
				PvTooltip.Line.of("Missing: " + u.missing(), REJECTED_C),
				PvTooltip.Line.of(u.maxed() ? "MAX" : FormatUtil.oneDecimal(u.fill() * 100) + "%", PvDraw.COLOR_MUTED)
			)));
		}
	}

	private static String shortComposterName(GardenSnapshot.ComposterUpgrade u) {
		if (u == null || u.id() == null) {
			return "?";
		}
		return switch (u.id().toLowerCase(Locale.ROOT)) {
			case "organic_matter_cap" -> "Organic Cap";
			case "cost_reduction" -> "Cost Reduction";
			case "multi_drop" -> "Multi Drop";
			case "fuel_cap" -> "Fuel Cap";
			case "speed" -> "Speed";
			default -> u.name();
		};
	}

	private void renderGreenhouse(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my) {
		int leftW = Math.max(150, w * 32 / 100);
		int rightW = w - leftW - GAP;
		PvDraw.innerPanel(g, x, y, leftW, h);
		PvDraw.innerPanel(g, x + leftW + GAP, y, rightW, h);

		GardenSnapshot.GreenhouseMeta meta = this.snapshot.greenhouseMeta();
		List<GardenSnapshot.GreenhouseRow> rows = this.snapshot.greenhouse();
		int analyzed = 0;
		for (GardenSnapshot.GreenhouseRow row : rows) {
			if (row.analyzed()) {
				analyzed++;
			}
		}

		int lx = x + PAD;
		int ly = y + PAD;
		int lw = leftW - PAD * 2;
		PvDraw.text(g, font, "Greenhouse", lx, ly, PvDraw.COLOR_MUTED);
		ly += font.lineHeight + 3;
		ly = statLine(g, font, "Analyzed", String.valueOf(analyzed), lx, ly, lw, COMPLETED_C) + 1;
		ly = statLine(g, font, "Discovered", String.valueOf(Math.max(0, rows.size() - analyzed)), lx, ly, lw, GOLD) + 1;
		ly = statLine(g, font, "Tracked", String.valueOf(rows.size()), lx, ly, lw, PvDraw.COLOR_TEXT) + 4;

		boolean island = this.snapshot.islandLoaded();
		PvDraw.text(g, font, "Desk upgrades", lx, ly, PvDraw.COLOR_MUTED);
		ly += font.lineHeight + 2;
		ly = statLine(g, font, "Slots unlocked",
			island ? String.valueOf(meta.slotsUnlocked()) : "...", lx, ly, lw, PvDraw.COLOR_TEXT) + 1;
		ly = statLine(g, font, "Yield",
			island ? String.valueOf(meta.yieldLevel()) : "...", lx, ly, lw, BAR_COMPOST) + 1;
		ly = statLine(g, font, "Plot limit",
			island ? String.valueOf(meta.plotLimitLevel()) : "...", lx, ly, lw, PvDraw.COLOR_ACCENT) + 1;
		ly = statLine(g, font, "Growth speed",
			island ? String.valueOf(meta.growthSpeedLevel()) : "...", lx, ly, lw, BAR_FARM) + 3;
		if (island && meta.lastGrowthStageMs() > 0L) {
			long raw = meta.lastGrowthStageMs();
			long ms = raw > 1_000_000_000_000L ? raw : raw * 1000L;
			long agoSec = Math.max(0L, (System.currentTimeMillis() - ms) / 1000L);
			statLine(g, font, "Last growth", agoSec < 60 ? agoSec + "s ago"
				: agoSec < 3600 ? (agoSec / 60) + "m ago"
				: (agoSec / 3600) + "h ago", lx, ly, lw, PvDraw.COLOR_MUTED);
		}

		int rx = x + leftW + GAP + PAD;
		int ry = y + PAD;
		int rw = rightW - PAD * 2;
		PvDraw.text(g, font, "Mutations", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 8;

		int cols = Math.max(8, Math.min(13, (rw + SLOT_GAP) / (GH_CELL_W + SLOT_GAP)));
		int cellW = Math.max(GH_CELL_W, (rw - (cols - 1) * SLOT_GAP) / cols);
		int cellH = GH_CELL_H;
		int gridW = cols * cellW + (cols - 1) * SLOT_GAP;
		int gridX = rx + Math.max(0, (rw - gridW) / 2);
		int gridRows = rows.isEmpty() ? 0 : (rows.size() + cols - 1) / cols;
		int gridTop = ry;
		int gridH = y + h - PAD - gridTop;
		this.scrollTop = gridTop;
		this.scrollH = gridH;
		this.maxScroll = Math.max(0, gridRows * (cellH + SLOT_GAP) - gridH);
		this.scroll = Math.min(this.scroll, this.maxScroll);
		this.leftMaxScroll = 0;

		ItemStack lime = new ItemStack(Items.LIME_DYE);
		ItemStack yellow = new ItemStack(Items.YELLOW_DYE);

		if (rows.isEmpty()) {
			PvDraw.textCentered(g, font, "No greenhouse crops", x + leftW + GAP + rightW / 2, y + h / 2, PvDraw.COLOR_MUTED);
			return;
		}

		g.enableScissor(rx, gridTop, rx + rw, gridTop + gridH);
		int baseY = gridTop - this.scroll;
		for (int i = 0; i < rows.size(); i++) {
			int col = i % cols;
			int row = i / cols;
			int bx = gridX + col * (cellW + SLOT_GAP);
			int by = baseY + row * (cellH + SLOT_GAP);
			if (by + cellH < gridTop || by > gridTop + gridH) {
				continue;
			}
			GardenSnapshot.GreenhouseRow gh = rows.get(i);
			drawCellBorder(g, bx, by, cellW, cellH);
			int iconX = bx + (cellW - ICON) / 2;
			drawIcon(g, gh.iconId(), iconX, by + 2, ICON, GardenData.greenhousePackModel(gh.id()));
			g.item(gh.analyzed() ? lime : yellow, iconX, by + 2 + ICON + 2);
			String status = gh.analyzed() ? "Analyzed" : "Discovered";
			int statusColor = gh.analyzed() ? COMPLETED_C : GOLD;
			this.zones.add(new HoverZone(bx, by, cellW, cellH, List.of(
				PvTooltip.Line.of(gh.name(), PvDraw.COLOR_TEXT),
				PvTooltip.Line.of(status, statusColor)
			)));
		}
		g.disableScissor();
	}

	private void renderJacob(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my) {
		int rightW = Math.max(200, w * 52 / 100);
		int leftW = w - rightW - GAP;
		PvDraw.innerPanel(g, x, y, leftW, h);

		int lx = x + PAD;
		int ly = y + PAD;
		int lw = leftW - PAD * 2;

		this.leftScrollX = lx;
		this.leftScrollW = lw;
		this.leftScrollTop = ly;
		this.leftScrollH = h - PAD * 2;

		int contentH = measureJacobLeft(font);
		this.leftMaxScroll = Math.max(0, contentH - this.leftScrollH);
		this.leftScroll = Math.min(this.leftScroll, this.leftMaxScroll);

		g.enableScissor(lx, this.leftScrollTop, lx + lw, this.leftScrollTop + this.leftScrollH);
		int cy = this.leftScrollTop - this.leftScroll;

		PvDraw.text(g, font, "Medals", lx, cy, PvDraw.COLOR_MUTED);
		cy += font.lineHeight + 3;
		GardenSnapshot.MedalCounts m = this.snapshot.medals();
		cy = statLine(g, font, "Bronze", FormatUtil.commas(m.bronze()), lx, cy, lw, BRONZE) + 1;
		cy = statLine(g, font, "Silver", FormatUtil.commas(m.silver()), lx, cy, lw, SILVER) + 1;
		cy = statLine(g, font, "Gold", FormatUtil.commas(m.gold()), lx, cy, lw, GOLD) + 1;
		cy = statLine(g, font, "Total", FormatUtil.commas(m.total()), lx, cy, lw, PvDraw.COLOR_TEXT) + 4;

		if (!this.snapshot.uniqueBrackets().isEmpty()) {
			PvDraw.text(g, font, "Unique brackets", lx, cy, PvDraw.COLOR_MUTED);
			cy += font.lineHeight + 2;
			for (GardenSnapshot.BracketCount b : this.snapshot.uniqueBrackets()) {
				cy = statLine(g, font, b.bracket(), String.valueOf(b.crops()), lx, cy, lw, PvDraw.COLOR_TEXT) + 1;
			}
			cy += 3;
		}

		List<GardenSnapshot.CropMedal> cropMedals = this.snapshot.cropMedals();
		PvDraw.text(g, font, "Crop medals", lx, cy, PvDraw.COLOR_MUTED);
		cy += font.lineHeight + 3;
		if (cropMedals.isEmpty()) {
			PvDraw.text(g, font, "None yet", lx, cy, PvDraw.COLOR_MUTED);
			cy += font.lineHeight + 4;
		} else {
			int rowH = Math.max(STAT_ROW, ICON + 2);
			int orbsW = 5 * ORB + 4 * ORB_GAP;
			for (GardenSnapshot.CropMedal medal : cropMedals) {
				drawIcon(g, medal.iconId(), lx, cy + (rowH - ICON) / 2, ICON, GardenData.cropPackModel(medal.id()));
				int textX = lx + ICON + 4;
				int nameMax = Math.max(8, lw - ICON - 8 - orbsW);
				String shown = trim(font, medal.name(), nameMax);
				PvDraw.text(g, font, shown, textX, cy + (rowH - font.lineHeight) / 2, PvDraw.COLOR_TEXT);
				drawMedalOrbs(g, lx + lw - orbsW, cy + (rowH - ORB) / 2, medal.filled());
				String tipMedal = switch (medal.filled()) {
					case 1 -> "Bronze";
					case 2 -> "Silver";
					case 3 -> "Gold";
					case 4 -> "Platinum";
					case 5 -> "Diamond";
					default -> "None";
				};
				this.zones.add(new HoverZone(lx, cy, lw, rowH, List.of(
					PvTooltip.Line.of(medal.name(), PvDraw.COLOR_TEXT),
					PvTooltip.Line.of(
						"Highest unique: " + tipMedal,
						medal.filled() <= 0 ? PvDraw.COLOR_MUTED : medalOrbColor(medal.filled() - 1)
					)
				)));
				cy += rowH + 1;
			}
			cy += 3;
		}

		if (!this.snapshot.perks().isEmpty()) {
			PvDraw.text(g, font, "Perks", lx, cy, PvDraw.COLOR_MUTED);
			cy += font.lineHeight + 2;
			for (Map.Entry<String, Integer> e : this.snapshot.perks().entrySet()) {
				cy = statLine(g, font, perkName(e.getKey()), String.valueOf(e.getValue()),
					lx, cy, lw, PvDraw.COLOR_ACCENT) + 1;
			}
		}
		g.disableScissor();

		drawJacobContestPanel(g, font, x + leftW + GAP, y, rightW, h, mx, my);
	}

	private void drawJacobContestPanel(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my) {
		this.jacobHitX = x;
		this.jacobHitY = y;
		this.jacobHitW = w;
		this.jacobHitH = h;

		boolean hovered = mx >= x && mx < x + w && my >= y && my < y + h;
		float flipProgress = 0F;
		boolean animating = this.jacobFlipStartMs != 0L;
		if (animating) {
			flipProgress = Math.min(1F, (System.currentTimeMillis() - this.jacobFlipStartMs) / (float) FLIP_MS);
			if (flipProgress >= 1F) {
				this.jacobExtrasFace = this.jacobFlipTarget;
				this.jacobFlipStartMs = 0L;
				animating = false;
				flipProgress = 0F;
			}
		}
		float eased = animating ? easeInOutCubic(flipProgress) : 0F;
		float angle = eased * (float) Math.PI;
		boolean showExtras = animating
			? (Math.cos(angle) < 0.0 ? this.jacobFlipTarget : this.jacobExtrasFace)
			: this.jacobExtrasFace;
		float scaleX = 1F;
		float scaleY = 1F;
		if (animating) {
			scaleX = Math.max(0.04F, Math.abs((float) Math.cos(angle)));
			scaleY = 1F - (1F - scaleX) * 0.06F;
		}

		float cxFlip = x + w / 2F;
		float cyFlip = y + h / 2F;
		g.pose().pushMatrix();
		g.pose().translate(cxFlip, cyFlip);
		g.pose().scale(scaleX, scaleY);
		g.pose().translate(-cxFlip, -cyFlip);

		PvDraw.innerPanel(g, x, y, w, h);
		if (hovered && !animating) {
			PvDraw.fill(g, x + 1, y + 1, w - 2, h - 2, PANEL_HOVER);
		}

		if (showExtras) {
			drawJacobExtrasFace(g, font, x, y, w, h);
		} else {
			drawJacobContestsFace(g, font, x, y, w, h);
		}

		g.pose().popMatrix();
	}

	private void drawJacobContestsFace(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h) {
		int rx = x + PAD;
		int ry = y + PAD;
		int rw = w - PAD * 2;
		PvDraw.text(g, font, "Contest history", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 3;

		String credit = Component.translatable("betterpv.garden.elite_credit").getString();
		int creditH = Math.round(font.lineHeight * CREDIT_SCALE) + 4;
		PvDraw.textScaled(g, font, credit, rx, y + h - PAD - Math.round(font.lineHeight * CREDIT_SCALE), CREDIT_C, CREDIT_SCALE);

		if (this.snapshot.contestsLoading()) {
			PvDraw.text(g, font, "Loading contests...", rx, ry, PvDraw.COLOR_MUTED);
			this.maxScroll = 0;
			return;
		}
		if (!this.snapshot.contestsError().isBlank() && this.snapshot.contests().isEmpty()) {
			PvDraw.text(g, font, trim(font, this.snapshot.contestsError(), rw), rx, ry, PvDraw.COLOR_MUTED);
			this.maxScroll = 0;
			return;
		}

		List<GardenSnapshot.ContestEntry> contests = this.snapshot.contests();
		int rowH = Math.max(STAT_ROW, ICON + 2);
		int listTop = ry;
		int listH = y + h - PAD - creditH - listTop;
		this.scrollTop = listTop;
		this.scrollH = Math.max(0, listH);
		this.maxScroll = Math.max(0, contests.size() * rowH - listH);
		this.scroll = Math.min(this.scroll, this.maxScroll);

		g.enableScissor(rx, listTop, rx + rw, listTop + listH);
		int yy = listTop - this.scroll;
		for (GardenSnapshot.ContestEntry c : contests) {
			if (yy + rowH >= listTop && yy < listTop + listH) {
				drawIcon(g, c.iconId(), rx, yy + (rowH - ICON) / 2, ICON, GardenData.cropPackModel(c.crop()));
				int textX = rx + ICON + 4;
				int textW = rw - ICON - 4;
				String medalLabel = medalDisplay(c.medal());
				String right = FormatUtil.shortXp(c.collected());
				if (!medalLabel.isBlank()) {
					right = medalLabel + "  " + right;
				}
				drawPair(g, font, c.cropName(), right, textX, yy + (rowH - font.lineHeight) / 2, textW,
					PvDraw.COLOR_TEXT, medalColor(c.medal()));
				List<PvTooltip.Line> tip = new ArrayList<>();
				tip.add(PvTooltip.Line.of(c.cropName(), PvDraw.COLOR_TEXT));
				tip.add(PvTooltip.Line.of("Collected: " + FormatUtil.commas(c.collected()), PvDraw.COLOR_MUTED));
				if (hasMedalLabel(c.medal())) {
					tip.add(PvTooltip.Line.of("Medal: " + title(c.medal()), medalColor(c.medal())));
				} else if ("unclaimable".equalsIgnoreCase(c.medal())) {
					tip.add(PvTooltip.Line.of("Medal: Unclaimable", PvDraw.COLOR_MUTED));
				}
				if (c.position() > 0) {
					tip.add(PvTooltip.Line.of(
						"#" + c.position() + (c.participants() > 0 ? " / " + c.participants() : ""),
						PvDraw.COLOR_MUTED
					));
				}
				if (c.timestampSeconds() > 0L) {
					tip.add(PvTooltip.Line.of(contestWhen(c.timestampSeconds()), PvDraw.COLOR_MUTED));
				}
				tip.add(PvTooltip.Line.of("Click to flip", PvDraw.COLOR_MUTED));
				this.zones.add(new HoverZone(rx, yy, rw, rowH, tip));
			}
			yy += rowH;
		}
		g.disableScissor();
	}

	private void drawJacobExtrasFace(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h) {
		int rx = x + PAD;
		int ry = y + PAD;
		int rw = w - PAD * 2;
		int bottom = y + h - PAD;

		PvDraw.text(g, font, "Unique golds", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 3;

		List<String> uniqueGolds = this.snapshot.uniqueGoldCrops();
		int goldBlockH;
		if (uniqueGolds.isEmpty()) {
			PvDraw.text(g, font, "None yet", rx, ry, PvDraw.COLOR_MUTED);
			goldBlockH = font.lineHeight + 4;
		} else {
			int cols = Math.max(4, Math.min(8, (rw + 2) / (ICON + 2)));
			int cell = ICON + 2;
			for (int i = 0; i < uniqueGolds.size(); i++) {
				String cropId = uniqueGolds.get(i);
				int col = i % cols;
				int row = i / cols;
				int bx = rx + col * cell;
				int by = ry + row * cell;
				String iconId = GardenData.cropIconId(cropId);
				drawIcon(g, iconId, bx, by, ICON, GardenData.cropPackModel(cropId));
				this.zones.add(new HoverZone(bx, by, ICON, ICON, List.of(
					PvTooltip.Line.of(GardenData.prettyCrop(cropId), GOLD),
					PvTooltip.Line.of("Unique gold medal", PvDraw.COLOR_MUTED)
				)));
			}
			int rows = (uniqueGolds.size() + cols - 1) / cols;
			goldBlockH = rows * cell + 4;
		}
		ry += goldBlockH;

		PvDraw.text(g, font, "Personal bests", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 3;

		List<GardenSnapshot.PersonalBest> pbs = this.snapshot.personalBests();
		int rowH = Math.max(STAT_ROW, ICON + 2);
		this.scrollTop = ry;
		this.scrollH = Math.max(0, bottom - ry);
		this.maxScroll = Math.max(0, pbs.size() * rowH - this.scrollH);
		this.scroll = Math.min(this.scroll, this.maxScroll);

		g.enableScissor(rx, this.scrollTop, rx + rw, this.scrollTop + this.scrollH);
		int yy = this.scrollTop - this.scroll;
		for (GardenSnapshot.PersonalBest pb : pbs) {
			if (yy + rowH >= this.scrollTop && yy < this.scrollTop + this.scrollH) {
				String iconId = GardenData.cropIconId(pb.id());
				drawIcon(g, iconId, rx, yy + (rowH - ICON) / 2, ICON, GardenData.cropPackModel(pb.id()));
				int textX = rx + ICON + 4;
				int textW = rw - ICON - 4;
				drawPair(g, font, pb.name(), FormatUtil.commas(pb.amount()), textX,
					yy + (rowH - font.lineHeight) / 2, textW, PvDraw.COLOR_TEXT, PvDraw.COLOR_ACCENT);
				this.zones.add(new HoverZone(rx, yy, rw, rowH, List.of(
					PvTooltip.Line.of(pb.name(), PvDraw.COLOR_TEXT),
					PvTooltip.Line.of(FormatUtil.commas(pb.amount()) + " collected", PvDraw.COLOR_MUTED),
					PvTooltip.Line.of("Click to flip", PvDraw.COLOR_MUTED)
				)));
			}
			yy += rowH;
		}
		g.disableScissor();
	}

	private int measureJacobLeft(Font font) {
		int h = font.lineHeight + 3 + STAT_ROW * 4 + 4;
		if (!this.snapshot.uniqueBrackets().isEmpty()) {
			h += font.lineHeight + 2 + this.snapshot.uniqueBrackets().size() * (STAT_ROW + 1) + 3;
		}
		h += font.lineHeight + 3;
		List<GardenSnapshot.CropMedal> medals = this.snapshot.cropMedals();
		if (medals.isEmpty()) {
			h += font.lineHeight + 4;
		} else {
			int rowH = Math.max(STAT_ROW, ICON + 2);
			h += medals.size() * (rowH + 1) + 3;
		}
		if (!this.snapshot.perks().isEmpty()) {
			h += font.lineHeight + 2 + this.snapshot.perks().size() * (STAT_ROW + 1);
		}
		return h;
	}

	private static void drawMedalOrbs(GuiGraphicsExtractor g, int x, int y, int filled) {
		for (int i = 0; i < 5; i++) {
			int ox = x + i * (ORB + ORB_GAP);
			int color = i < filled ? medalOrbColor(i) : MEDAL_ORB_EMPTY;
			drawOrb(g, ox, y, ORB, color);
		}
	}

	private static int medalOrbColor(int index) {
		return switch (index) {
			case 0 -> BRONZE;
			case 1 -> SILVER;
			case 2 -> GOLD;
			case 3 -> PLATINUM;
			case 4 -> DIAMOND;
			default -> MEDAL_ORB_EMPTY;
		};
	}

	private static void drawOrb(GuiGraphicsExtractor g, int x, int y, int size, int argb) {
		int r = size / 2;
		int r2 = r * r;
		for (int dy = 0; dy < size; dy++) {
			for (int dx = 0; dx < size; dx++) {
				int cx = dx - r;
				int cy = dy - r;
				if (cx * cx + cy * cy <= r2) {
					PvDraw.fill(g, x + dx, y + dy, 1, 1, argb);
				}
			}
		}
	}

	private static float easeInOutCubic(float t) {
		return t < 0.5F ? 4F * t * t * t : 1F - (float) Math.pow(-2F * t + 2F, 3) / 2F;
	}

	private void prefetch() {
		List<String> ids = new ArrayList<>();
		for (GardenSnapshot.VisitorRow v : this.snapshot.visitors()) {
			if (v.npcItemId() != null && !v.npcItemId().isBlank()) {
				ids.add(v.npcItemId());
			}
		}
		for (GardenSnapshot.CropRow c : this.snapshot.crops()) {
			ids.add(c.iconId());
		}
		for (GardenSnapshot.ChipEntry chip : this.snapshot.gardenChips()) {
			if (chip.iconId() != null && !chip.iconId().isBlank()) {
				ids.add(chip.iconId());
			}
		}
		for (GardenSnapshot.GreenhouseRow g : this.snapshot.greenhouse()) {
			if (g.iconId() != null && !g.iconId().isBlank()) {
				ids.add(g.iconId());
			}
			String pack = GardenData.greenhousePackModel(g.id());
			if (!pack.isBlank()) {
				SkyBlockItemFactory.customIconModel(g.iconId(), pack);
			}
		}
		for (GardenSnapshot.ContestEntry c : this.snapshot.contests()) {
			if (c.iconId() != null && !c.iconId().isBlank()) {
				ids.add(c.iconId());
			}
		}
		for (GardenSnapshot.CropMedal medal : this.snapshot.cropMedals()) {
			if (medal.iconId() != null && !medal.iconId().isBlank()) {
				ids.add(medal.iconId());
			}
		}
		for (String cropId : this.snapshot.uniqueGoldCrops()) {
			ids.add(GardenData.cropIconId(cropId));
		}
		for (GardenSnapshot.PersonalBest pb : this.snapshot.personalBests()) {
			ids.add(GardenData.cropIconId(pb.id()));
		}
		if (!ids.isEmpty()) {
			SkyBlockItemFactory.prefetchIds(ids);
		}
	}

	private int drawBar(
		GuiGraphicsExtractor g, Font font, String label, String value, float fill, boolean maxed,
		int color, String hover, int x, int y, int w, int mx, int my
	) {
		String shown = fitValue(font, label, value == null ? "" : value, w);
		PvDraw.labeledBar(g, font, trim(font, label, Math.max(24, w - font.width(shown) - 8)),
			shown, fill, x, y, w, color, maxed);
		int bottom = y + font.lineHeight + BAR_LABEL_GAP + PvDraw.BAR_HEIGHT;
		if (hover != null && !hover.isBlank()) {
			this.zones.add(new HoverZone(x, y, w, bottom - y, List.of(PvTooltip.Line.of(hover, PvDraw.COLOR_TEXT))));
		}
		return bottom;
	}

	private static int barRowH(Font font) {
		return font.lineHeight + BAR_LABEL_GAP + PvDraw.BAR_HEIGHT + BAR_AFTER;
	}

	private static int statLine(GuiGraphicsExtractor g, Font font, String label, String value, int x, int y, int w, int valueColor) {
		drawPair(g, font, label, value, x, y, w, PvDraw.COLOR_MUTED, valueColor);
		return y + STAT_ROW;
	}

	private static void drawPair(
		GuiGraphicsExtractor g, Font font, String left, String right, int x, int y, int w, int leftColor, int rightColor
	) {
		String r = right == null ? "" : right;
		int leftMax = Math.max(8, w - font.width(r) - 6);
		PvDraw.text(g, font, trim(font, left, leftMax), x, y, leftColor);
		PvDraw.textRight(g, font, r, x + w, y, rightColor);
	}

	private static void drawCellBorder(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.outline(x, y, w, h, CELL_BORDER);
	}

	private static void drawIcon(GuiGraphicsExtractor g, String id, int x, int y, int size, String packModel) {
		Identifier texture = null;
		if (packModel != null && !packModel.isBlank()) {
			texture = SkyBlockItemFactory.customIconModel(id, packModel);
		}
		if (texture == null) {
			texture = id == null || id.isBlank() ? null : SkyBlockItemFactory.customIcon(id);
		}
		if (texture != null) {
			int tex = SkyBlockItemFactory.customIconSize(id);
			g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, size, size, tex, tex, tex, tex);
			return;
		}
		ItemStack vanilla = vanillaFallback(id);
		if (!vanilla.isEmpty()) {
			drawStack(g, vanilla, x, y, size);
			return;
		}
		ItemStack icon = id == null || id.isBlank() ? ItemStack.EMPTY : SkyBlockItemFactory.iconStack(id);
		if (isTexturedHead(icon) || (!icon.isEmpty() && !icon.is(Items.PAPER) && !icon.is(Items.PLAYER_HEAD))) {
			drawStack(g, icon, x, y, size);
			return;
		}
		String upper = id == null ? "" : id.toUpperCase(Locale.ROOT);
		if (upper.endsWith("_NPC") || upper.contains("MAYOR") || upper.endsWith("_MONSTER")) {
			drawStack(g, new ItemStack(Items.VILLAGER_SPAWN_EGG), x, y, size);
			return;
		}
		drawStack(g, new ItemStack(Items.PAPER), x, y, size);
	}

	private static boolean isTexturedHead(ItemStack icon) {
		if (icon == null || icon.isEmpty() || !icon.is(Items.PLAYER_HEAD)) {
			return false;
		}
		var profile = icon.get(DataComponents.PROFILE);
		if (profile == null) {
			return false;
		}
		try {
			var props = profile.partialProfile().properties().get("textures");
			if (props == null || props.isEmpty()) {
				return false;
			}
			for (var prop : props) {
				if (prop != null && prop.value() != null && !prop.value().isBlank()) {
					return true;
				}
			}
		} catch (Exception ignored) {
		}
		return false;
	}

	private static void drawStack(GuiGraphicsExtractor g, ItemStack icon, int x, int y, int size) {
		if (size == ICON) {
			g.item(icon, x, y);
			return;
		}
		float scale = size / (float) ICON;
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.item(icon, 0, 0);
		g.pose().popMatrix();
	}

	private static ItemStack vanillaFallback(String id) {
		if (id == null || id.isBlank()) {
			return ItemStack.EMPTY;
		}
		return switch (id.toUpperCase(Locale.ROOT)) {
			case "CLOCK" -> new ItemStack(Items.CLOCK);
			case "GOLDEN_HOE" -> new ItemStack(Items.GOLDEN_HOE);
			case "COAL_BLOCK" -> new ItemStack(Items.COAL_BLOCK);
			case "GOLD_INGOT" -> new ItemStack(Items.GOLD_INGOT);
			case "COMPOSTER" -> new ItemStack(Items.COMPOSTER);
			case "WHEAT" -> new ItemStack(Items.WHEAT);
			case "CARROT_ITEM", "CARROT" -> new ItemStack(Items.CARROT);
			case "POTATO_ITEM", "POTATO" -> new ItemStack(Items.POTATO);
			case "PUMPKIN" -> new ItemStack(Items.PUMPKIN);
			case "SUGAR_CANE" -> new ItemStack(Items.SUGAR_CANE);
			case "MELON" -> new ItemStack(Items.MELON_SLICE);
			case "CACTUS" -> new ItemStack(Items.CACTUS);
			case "INK_SACK:3", "INK_SACK-3", "COCOA_BEANS" -> new ItemStack(Items.COCOA_BEANS);
			case "RED_MUSHROOM", "MUSHROOM" -> new ItemStack(Items.RED_MUSHROOM);
			case "NETHER_STALK", "NETHER_WART" -> new ItemStack(Items.NETHER_WART);
			case "DOUBLE_PLANT", "SUNFLOWER" -> new ItemStack(Items.SUNFLOWER);
			case "MOONFLOWER" -> new ItemStack(Items.BLUE_ORCHID);
			case "WILD_ROSE" -> new ItemStack(Items.ROSE_BUSH);
			case "SEEDS", "WHEAT_SEEDS" -> new ItemStack(Items.WHEAT_SEEDS);
			default -> ItemStack.EMPTY;
		};
	}

	private static String chipPackModel(String id) {
		String key = GardenData.normalizeChipKey(id);
		if (key.isBlank()) {
			return "";
		}
		String file = switch (key) {
			case "vermin_vaporize" -> "vermin_vaporizer_chip";
			default -> key + "_chip";
		};
		return "hypixel_skyblock:item/island_relevant/garden/chips/" + file;
	}

	private List<PvTooltip.Line> cropMilestoneTip(GardenSnapshot.CropRow crop) {
		List<PvTooltip.Line> tip = new ArrayList<>();
		tip.add(PvTooltip.Line.of(crop.name(), PvDraw.COLOR_TEXT));
		tip.add(PvTooltip.Line.of(
			"Milestone " + crop.milestone() + (crop.milestoneMaxed() ? " (MAX)" : ""),
			PvDraw.COLOR_ACCENT
		));
		if (crop.milestoneMaxed()) {
			tip.add(PvTooltip.Line.of("Overflow: " + FormatUtil.commas(crop.collected()), PvDraw.COLOR_MUTED));
		} else {
			GardenData.LevelProgress ms = GardenData.cropMilestone(crop.id(), crop.collected());
			tip.add(PvTooltip.Line.of(
				FormatUtil.commas(ms.intoLevel()) + " / " + FormatUtil.commas(ms.needForNext()),
				PvDraw.COLOR_MUTED
			));
		}
		tip.add(PvTooltip.Line.of("Upgrade: +" + crop.upgradeLevel(), PvDraw.COLOR_MUTED));
		if (this.snapshot.hasUniqueGold(crop.id())) {
			tip.add(PvTooltip.Line.of("Unique gold", GOLD));
		}
		return tip;
	}

	private static List<PvTooltip.Line> farmingWeightHover(GardenSnapshot.FarmingWeightInfo weight) {
		List<PvTooltip.Line> tip = new ArrayList<>();
		tip.add(PvTooltip.Line.of("Farming weight", PvDraw.COLOR_GOLD));
		tip.add(PvTooltip.Line.of("Total: " + FormatUtil.oneDecimal(weight.displayTotal()), PvDraw.COLOR_TEXT));
		List<Map.Entry<String, Double>> bonuses = new ArrayList<>(weight.bonusWeight().entrySet());
		bonuses.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
		List<Map.Entry<String, Double>> shownBonus = new ArrayList<>();
		for (Map.Entry<String, Double> e : bonuses) {
			if (e.getValue() != null && e.getValue() > 0) {
				shownBonus.add(e);
			}
		}
		if (!shownBonus.isEmpty()) {
			tip.add(PvTooltip.Line.of("Bonus", PvDraw.COLOR_ACCENT));
			for (Map.Entry<String, Double> e : shownBonus) {
				tip.add(PvTooltip.Line.of(
					prettyBonusWeight(e.getKey()) + ": " + FormatUtil.oneDecimal(e.getValue()),
					PvDraw.COLOR_MUTED
				));
			}
		}
		List<Map.Entry<String, Double>> crops = new ArrayList<>(weight.cropWeight().entrySet());
		crops.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
		List<Map.Entry<String, Double>> shownCrops = new ArrayList<>();
		for (Map.Entry<String, Double> e : crops) {
			if (e.getValue() != null && e.getValue() > 0) {
				shownCrops.add(e);
			}
		}
		if (!shownCrops.isEmpty()) {
			tip.add(PvTooltip.Line.of("Crops", PvDraw.COLOR_ACCENT));
			int shown = 0;
			for (Map.Entry<String, Double> e : shownCrops) {
				if (shown++ >= 12) {
					break;
				}
				tip.add(PvTooltip.Line.of(
					GardenData.prettyCrop(e.getKey()) + ": " + FormatUtil.oneDecimal(e.getValue()),
					PvDraw.COLOR_MUTED
				));
			}
		}
		tip.add(PvTooltip.Line.of("Provided by elitebot.dev", CREDIT_C));
		return tip;
	}

	private static String prettyBonusWeight(String key) {
		if (key == null || key.isBlank()) {
			return "?";
		}
		return switch (key.toLowerCase(Locale.ROOT)) {
			case "farming_level", "farminglevel" -> "Farming level";
			case "anita" -> "Anita";
			case "contests", "jacob_contests", "jacob" -> "Contests";
			case "minions" -> "Minions";
			case "pests" -> "Pests";
			default -> title(key.replace('-', '_'));
		};
	}

	private static String contestWhen(long timestampSeconds) {
		long sec = timestampSeconds;
		if (sec > 10_000_000_000L) {
			sec = sec / 1000L;
		}
		long ago = Math.max(0L, (System.currentTimeMillis() / 1000L) - sec);
		if (ago < 60) {
			return ago + "s ago";
		}
		if (ago < 3600) {
			return (ago / 60) + "m ago";
		}
		if (ago < 86_400) {
			return (ago / 3600) + "h ago";
		}
		if (ago < 86_400L * 45L) {
			return (ago / 86_400L) + "d ago";
		}
		return (ago / (86_400L * 30L)) + "mo ago";
	}

	private static int rarityColor(String rarity) {
		if (rarity == null) {
			return PvDraw.COLOR_MUTED;
		}
		return switch (rarity.toUpperCase(Locale.ROOT)) {
			case "COMMON" -> 0xFFFFFFFF;
			case "UNCOMMON" -> 0xFF55FF55;
			case "RARE" -> 0xFF5555FF;
			case "EPIC" -> 0xFFAA00AA;
			case "LEGENDARY" -> 0xFFFFAA00;
			case "MYTHIC" -> 0xFFFF55FF;
			case "SPECIAL", "VERY_SPECIAL" -> 0xFFFF5555;
			default -> PvDraw.COLOR_MUTED;
		};
	}

	private static String fitValue(Font font, String label, String value, int w) {
		if (value == null || value.isBlank()) {
			return "";
		}
		int maxValue = w - font.width(label) - 10;
		if (maxValue < 12) {
			return "";
		}
		return font.width(value) <= maxValue ? value : trim(font, value, maxValue);
	}

	private static String trim(Font font, String text, int maxW) {
		if (text == null) {
			return "";
		}
		if (font.width(text) <= maxW) {
			return text;
		}
		String ell = "...";
		int ellW = font.width(ell);
		if (maxW <= ellW) {
			return "";
		}
		StringBuilder sb = new StringBuilder(text);
		while (sb.length() > 0 && font.width(sb.toString()) + ellW > maxW) {
			sb.setLength(sb.length() - 1);
		}
		return sb + ell;
	}

	private static String perkName(String id) {
		if (id == null) {
			return "?";
		}
		return switch (id.toLowerCase(Locale.ROOT)) {
			case "double_drops" -> "Double Drops";
			case "farming_level_cap" -> "Farming Cap";
			case "personal_bests" -> "Personal Bests";
			default -> title(id.replace('_', ' '));
		};
	}

	private static String title(String raw) {
		if (raw == null || raw.isBlank()) {
			return "?";
		}
		String[] parts = raw.toLowerCase(Locale.ROOT).split("[\\s_]+");
		StringBuilder out = new StringBuilder();
		for (String p : parts) {
			if (p.isEmpty()) {
				continue;
			}
			if (out.length() > 0) {
				out.append(' ');
			}
			out.append(Character.toUpperCase(p.charAt(0)));
			if (p.length() > 1) {
				out.append(p.substring(1));
			}
		}
		return out.toString();
	}

	private static boolean hasMedalLabel(String medal) {
		if (medal == null || medal.isBlank()) {
			return false;
		}
		String m = medal.toLowerCase(Locale.ROOT);
		return !"none".equals(m) && !"unclaimable".equals(m);
	}

	private static String medalDisplay(String medal) {
		if (!hasMedalLabel(medal)) {
			return "";
		}
		return title(medal);
	}

	private static int medalColor(String medal) {
		if (medal == null) {
			return PvDraw.COLOR_MUTED;
		}
		return switch (medal.toLowerCase(Locale.ROOT)) {
			case "bronze" -> BRONZE;
			case "silver" -> SILVER;
			case "gold" -> GOLD;
			case "platinum" -> PLATINUM;
			case "diamond" -> DIAMOND;
			case "ghost" -> GHOST;
			default -> PvDraw.COLOR_MUTED;
		};
	}

	private void drawHover(GuiGraphicsExtractor g, Font font, int mx, int my, int screenW, int screenH) {
		for (HoverZone zone : this.zones) {
			if (mx >= zone.x && mx < zone.x + zone.w && my >= zone.y && my < zone.y + zone.h) {
				PvTooltip.drawStyled(g, font, zone.lines, mx, my, screenW, screenH);
				return;
			}
		}
	}

	private record HoverZone(int x, int y, int w, int h, List<PvTooltip.Line> lines) {
	}
}
