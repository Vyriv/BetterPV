package dev.vy.betterpv.client.gui.foraging;

import dev.vy.betterpv.client.data.DungeonSnapshot;
import dev.vy.betterpv.client.data.FishFamilyData;
import dev.vy.betterpv.client.data.FormatUtil;
import dev.vy.betterpv.client.data.ForagingHotfData;
import dev.vy.betterpv.client.data.ForagingSnapshot;
import dev.vy.betterpv.client.data.InventorySnapshot;
import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.PvTooltip;
import dev.vy.betterpv.client.gui.inventories.SkyBlockItemFactory;
import dev.vy.betterpv.client.gui.nav.PvSubTab;
import dev.vy.betterpv.client.price.ItemPricer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Foraging: Overview / HOTF / Hunting / Attribute Shards. */
public final class ForagingPage {
	private static final int PAD = 6;
	private static final int GAP = 6;
	private static final int BAR_LABEL_GAP = 2;
	private static final int BAR_AFTER = 4;
	private static final int STAT_ROW = 12;
	private static final int FLIP_MS = 480;
	private static final int PANEL_HOVER = 0x0AFFFFFF;
	private static final int BAR_FORAGE = 0xFF55AA55;
	private static final int BAR_HUNT = 0xFFAA7733;
	private static final int ENABLED = 0xFF55FF55;
	private static final int DISABLED = 0xFF9A9AAC;
	private static final int ICON = 14;
	private static final int CHIP = 16;
	private static final int SLOT = 18;
	private static final int SLOT_GAP = 3;
	private static final int ITEM_SLOT_BG = 0xFF101018;
	private static final int ITEM_SLOT_BORDER = 0xFF2A2A35;
	private static final int ITEM_SLOT_LOCKED_BG = 0xFF3A1010;
	private static final int ITEM_SLOT_LOCKED_BORDER = 0xFFCC3333;
	private static final int FOREST_COLOR = 0xFF55AA55;
	private static final int COLOR_MAXED = 0xFF7CFF9A;
	private static final int SEP_GAP = 10;

	private ForagingSnapshot snapshot = ForagingSnapshot.empty();
	private PvSubTab lastSub;
	private int scroll;
	private int maxScroll;
	private int scrollTop;
	private int scrollH;
	private int attrGridScroll;
	private int attrGridMaxScroll;
	private int attrGridX;
	private int attrGridY;
	private int attrGridW;
	private int attrGridH;
	private int attrListScroll;
	private int attrListMaxScroll;
	private int attrListX;
	private int attrListY;
	private int attrListW;
	private int attrListH;
	private final List<HoverZone> zones = new ArrayList<>();

	private boolean rightEssenceFace;
	private long rightFlipStartMs;
	private boolean rightFlipTarget;
	private int rightHitX;
	private int rightHitY;
	private int rightHitW;
	private int rightHitH;

	public void apply(ForagingSnapshot snapshot) {
		this.snapshot = snapshot == null ? ForagingSnapshot.empty() : snapshot;
		this.scroll = 0;
		this.attrGridScroll = 0;
		this.attrListScroll = 0;
		this.zones.clear();
		this.rightEssenceFace = false;
		this.rightFlipStartMs = 0L;
		ForagingHotfData.ensureLoaded();
	}

	public ForagingSnapshot snapshot() {
		return this.snapshot;
	}

	public boolean mouseClicked(double mx, double my) {
		if (this.lastSub != PvSubTab.FORAGING_OVERVIEW) {
			return false;
		}
		if (hit(mx, my, this.rightHitX, this.rightHitY, this.rightHitW, this.rightHitH)) {
			if (this.rightFlipStartMs != 0L) {
				return true;
			}
			this.rightFlipTarget = !this.rightEssenceFace;
			this.rightFlipStartMs = System.currentTimeMillis();
			return true;
		}
		return false;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY, PvSubTab sub) {
		if (sub == PvSubTab.FORAGING_ATTRIBUTE_SHARDS) {
			int delta = scrollY > 0 ? -14 : 14;
			boolean overGrid = hit(mouseX, mouseY, this.attrGridX, this.attrGridY, this.attrGridW, this.attrGridH);
			boolean overList = hit(mouseX, mouseY, this.attrListX, this.attrListY, this.attrListW, this.attrListH);
			if (overGrid && this.attrGridMaxScroll > 0) {
				int next = Math.max(0, Math.min(this.attrGridMaxScroll, this.attrGridScroll + delta));
				if (next != this.attrGridScroll) {
					this.attrGridScroll = next;
				}
				return true;
			}
			if (overList && this.attrListMaxScroll > 0) {
				int next = Math.max(0, Math.min(this.attrListMaxScroll, this.attrListScroll + delta));
				if (next != this.attrListScroll) {
					this.attrListScroll = next;
				}
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

	public void render(
		GuiGraphicsExtractor g, Font font, PvSubTab sub,
		int x, int y, int w, int h, int mouseX, int mouseY, int screenW, int screenH
	) {
		this.zones.clear();
		PvSubTab mode = sub == null ? PvSubTab.FORAGING_OVERVIEW : sub;
		if (mode != this.lastSub) {
			this.lastSub = mode;
			this.scroll = 0;
			this.attrGridScroll = 0;
			this.attrListScroll = 0;
			if (mode != PvSubTab.FORAGING_OVERVIEW) {
				this.rightEssenceFace = false;
				this.rightFlipStartMs = 0L;
				this.rightHitW = 0;
			}
		}

		switch (mode) {
			case FORAGING_HOTF -> renderHotf(g, font, x, y, w, h, mouseX, mouseY);
			case FORAGING_HUNTING -> renderHunting(g, font, x, y, w, h, mouseX, mouseY);
			case FORAGING_ATTRIBUTE_SHARDS -> renderAttributeShards(g, font, x, y, w, h, mouseX, mouseY);
			default -> renderOverview(g, font, x, y, w, h, mouseX, mouseY);
		}
		drawHover(g, font, mouseX, mouseY, screenW, screenH);
	}

	private void renderOverview(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my
	) {
		int rightW = Math.max(200, w * 52 / 100);
		int leftW = w - rightW - GAP;
		PvDraw.innerPanel(g, x, y, leftW, h);
		drawOverviewLeft(g, font, x, y, leftW, h, mx, my);
		drawRightFlipPanel(g, font, x + leftW + GAP, y, rightW, h, mx, my);
	}

	private void drawRightFlipPanel(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my
	) {
		this.rightHitX = x;
		this.rightHitY = y;
		this.rightHitW = w;
		this.rightHitH = h;

		boolean hovered = mx >= x && mx < x + w && my >= y && my < y + h;
		float flipProgress = 0F;
		boolean animating = this.rightFlipStartMs != 0L;
		if (animating) {
			flipProgress = Math.min(1F, (System.currentTimeMillis() - this.rightFlipStartMs) / (float) FLIP_MS);
			if (flipProgress >= 1F) {
				this.rightEssenceFace = this.rightFlipTarget;
				this.rightFlipStartMs = 0L;
				animating = false;
				flipProgress = 0F;
			}
		}
		float eased = animating ? easeInOutCubic(flipProgress) : 0F;
		float angle = eased * (float) Math.PI;
		boolean showEssence = animating
			? (Math.cos(angle) < 0.0 ? this.rightFlipTarget : this.rightEssenceFace)
			: this.rightEssenceFace;
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

		if (showEssence) {
			drawForestEssenceFace(g, font, x, y, w, h);
		} else {
			drawContestsFace(g, font, x, y, w, h);
		}

		g.pose().popMatrix();

		if (hovered && !animating) {
			this.zones.add(HoverZone.of(x, y, w, h, List.of(
				PvTooltip.Line.of("Click to flip", PvDraw.COLOR_MUTED)
			)));
		}
	}

	private void drawOverviewLeft(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my
	) {
		int lx = x + PAD;
		int ly = y + PAD;
		int lw = w - PAD * 2;
		int bottom = y + h - PAD;

		ly = drawBar(g, font, "Foraging", String.valueOf(this.snapshot.foragingLevel()),
			this.snapshot.foragingFill(), this.snapshot.foragingMaxed(), BAR_FORAGE,
			this.snapshot.foragingHover(), lx, ly, lw) + BAR_AFTER + 2;

		ly = statLine(g, font, "Park race PB", formatRaceMs(this.snapshot.raceBestMs()),
			lx, ly, lw, PvDraw.COLOR_ACCENT);
		ly = sectionSeparator(g, font, x, ly, w);

		PvDraw.text(g, font, "Fish Family", lx, ly, PvDraw.COLOR_MUTED);
		ly += font.lineHeight + 3;
		Set<String> discovered = new HashSet<>();
		for (String id : this.snapshot.fishFamily()) {
			if (id != null && !id.isBlank()) {
				discovered.add(id.toUpperCase(Locale.ROOT));
			}
		}
		List<String> catalog = new ArrayList<>(FishFamilyData.all());
		Set<String> known = new HashSet<>();
		for (String id : catalog) {
			known.add(id.toUpperCase(Locale.ROOT));
		}
		for (String id : discovered) {
			if (known.add(id)) {
				catalog.add(id);
			}
		}
		int cols = Math.max(1, Math.min(catalog.size(), Math.max(1, (lw + SLOT_GAP) / (SLOT + SLOT_GAP))));
		for (int i = 0; i < catalog.size(); i++) {
			int col = i % cols;
			int row = i / cols;
			int bx = lx + col * (SLOT + SLOT_GAP);
			int by = ly + row * (SLOT + SLOT_GAP);
			String id = catalog.get(i);
			boolean found = discovered.contains(id.toUpperCase(Locale.ROOT));
			boolean hovered = mx >= bx && mx < bx + SLOT && my >= by && my < by + SLOT;
			PvDraw.fill(g, bx, by, SLOT, SLOT, ITEM_SLOT_BG);
			g.outline(bx, by, SLOT, SLOT, hovered ? PvDraw.COLOR_ACCENT : ITEM_SLOT_BORDER);
			if (found) {
				drawFishFamilyIcon(g, id, bx + 1, by + 1);
			} else {
				g.item(new ItemStack(Items.GRAY_DYE), bx + 1, by + 1);
			}
			this.zones.add(HoverZone.of(bx, by, SLOT, SLOT, List.of(
				PvTooltip.Line.of(FishFamilyData.displayName(id), found ? PvDraw.COLOR_TEXT : PvDraw.COLOR_MUTED),
				PvTooltip.Line.of(found ? "Shown to Coral" : "Undiscovered", PvDraw.COLOR_MUTED)
			)));
		}
		int rows = (catalog.size() + cols - 1) / cols;
		ly += rows * SLOT + Math.max(0, rows - 1) * SLOT_GAP + 6;

		ly = sectionSeparator(g, font, x, ly, w);
		ly = statLine(g, font, "Hina Chapter", String.valueOf(this.snapshot.hinaTier()),
			lx, ly, lw, PvDraw.COLOR_ACCENT);

		if (this.snapshot.hasGalateaBeacon()) {
			ly = sectionSeparator(g, font, x, ly, w);
			ly = statLine(g, font, "Galatea Beacon",
				this.snapshot.galateaBeacon() + " / 10",
				lx, ly, lw, PvDraw.COLOR_GOLD);
		}
		this.maxScroll = 0;
		this.scroll = 0;
	}

	private void drawMelodySection(
		GuiGraphicsExtractor g, Font font, int lx, int ly, int lw, int bottom
	) {
		PvDraw.text(g, font, "Melodies Harp", lx, ly, PvDraw.COLOR_MUTED);
		ly += font.lineHeight + 2;
		ly = statLine(g, font, "Talisman", this.snapshot.harpTalisman() ? "Claimed" : "Unclaimed",
			lx, ly, lw, this.snapshot.harpTalisman() ? ENABLED : PvDraw.COLOR_MUTED) + 2;

		List<ForagingSnapshot.HarpSong> songs = this.snapshot.harpSongs();
		if (songs.isEmpty()) {
			PvDraw.text(g, font, "No songs", lx, ly, PvDraw.COLOR_MUTED);
			return;
		}

		int availH = Math.max(font.lineHeight, bottom - ly);
		int cellW = ICON + 4 + font.width("100%") + 2;
		int cols = Math.max(2, Math.min(songs.size(), Math.max(1, lw / Math.max(1, cellW + 2))));
		int rowsNeeded = (songs.size() + cols - 1) / cols;
		int cellH = Math.max(ICON + 2, Math.min(ICON + 4, availH / Math.max(1, rowsNeeded)));
		int gap = Math.max(1, Math.min(3, (availH - rowsNeeded * cellH) / Math.max(1, rowsNeeded)));

		ItemStack book = new ItemStack(Items.BOOK);
		for (int i = 0; i < songs.size(); i++) {
			ForagingSnapshot.HarpSong song = songs.get(i);
			int col = i % cols;
			int row = i / cols;
			int bx = lx + col * (cellW + 2);
			int by = ly + row * (cellH + gap);
			if (by + ICON > bottom) {
				break;
			}
			double pct = song.best() <= 1.0 ? song.best() * 100.0 : song.best();
			g.item(book, bx, by);
			String pctText = Math.round(pct) + "%";
			PvDraw.text(g, font, pctText, bx + ICON + 2, by + Math.max(0, (ICON - font.lineHeight) / 2),
				PvDraw.COLOR_ACCENT);
			this.zones.add(HoverZone.of(bx, by, cellW, cellH, List.of(
				PvTooltip.Line.of(song.name(), PvDraw.COLOR_TEXT),
				PvTooltip.Line.of("Best: " + FormatUtil.oneDecimal(pct) + "%", PvDraw.COLOR_ACCENT),
				PvTooltip.Line.of("Completions: " + song.completions(), PvDraw.COLOR_MUTED),
				PvTooltip.Line.of("Perfects: " + song.perfects(), PvDraw.COLOR_GOLD)
			)));
		}
	}

	private void drawContestsFace(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h) {
		int rx = x + PAD;
		int ry = y + PAD;
		int rw = w - PAD * 2;

		PvDraw.text(g, font, "Tree gifts", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 3;
		List<ForagingSnapshot.TreeGift> gifts = this.snapshot.treeGifts();
		if (gifts.isEmpty()) {
			PvDraw.text(g, font, "None", rx, ry, PvDraw.COLOR_MUTED);
			ry += STAT_ROW + 2;
		} else {
			for (ForagingSnapshot.TreeGift gift : gifts) {
				String right = FormatUtil.commas(gift.count())
					+ (gift.milestoneTier() > 0 ? " · T" + gift.milestoneTier() : "");
				ry = statLine(g, font, gift.name(), right, rx, ry, rw, PvDraw.COLOR_TEXT) + 1;
			}
			ry += 2;
		}

		ry = sectionSeparator(g, font, x, ry, w);
		PvDraw.text(g, font, "Starlyn PBs", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 3;
		List<ForagingSnapshot.StarlynBest> bests = this.snapshot.starlynBests();
		if (bests.isEmpty()) {
			PvDraw.text(g, font, "None", rx, ry, PvDraw.COLOR_MUTED);
			ry += STAT_ROW + 2;
		} else {
			for (ForagingSnapshot.StarlynBest best : bests) {
				ry = statLine(g, font, best.name(), FormatUtil.commas(best.amount()),
					rx, ry, rw, PvDraw.COLOR_GOLD) + 1;
			}
			ry += 2;
		}

		ry = sectionSeparator(g, font, x, ry, w);
		PvDraw.text(g, font, "Forests Whispers", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 3;
		statLine(g, font, "Current", FormatUtil.commas(this.snapshot.whispers()),
			rx, ry, rw, PvDraw.COLOR_ACCENT);
		ry += STAT_ROW + 1;
		statLine(g, font, "Spent", FormatUtil.commas(this.snapshot.whispersSpent()),
			rx, ry, rw, PvDraw.COLOR_MUTED);
	}

	private void drawForestEssenceFace(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h) {
		int cx = x + PAD;
		int cy = y + PAD;
		int innerW = w - PAD * 2;
		int bottom = y + h - PAD;
		DungeonSnapshot.EssenceShop shop = this.snapshot.forestShop();

		List<ForagingSnapshot.HarpSong> songs = this.snapshot.harpSongs();
		int melodyHeader = font.lineHeight + 2 + STAT_ROW + 2;
		int melodyCols = Math.max(2, Math.min(Math.max(1, songs.size()), Math.max(1, innerW / 40)));
		int melodyRows = songs.isEmpty() ? 1 : (songs.size() + melodyCols - 1) / melodyCols;
		int melodyBlock = melodyHeader + melodyRows * (ICON + 3) + SEP_GAP + 4;
		int shopsBottom = Math.max(cy + 48, bottom - melodyBlock);

		// Full 16px icons — no fractional scale (that softens item textures).
		int headerH = Math.max(16, font.lineHeight + 2);
		int headerTextMid = Math.max(0, (headerH - font.lineHeight) / 2);
		g.item(essenceIcon(shop.iconId()), cx, cy + Math.max(0, (headerH - 16) / 2));
		String bal = FormatUtil.commas(shop.balance());
		int balW = PvDraw.widthBold(font, bal);
		int headerLabelX = cx + 16 + 4;
		int nameMax = Math.max(8, innerW - (headerLabelX - cx) - balW - 4);
		PvDraw.textBold(g, font, trim(font, shop.name() + " Essence", nameMax),
			headerLabelX, cy + headerTextMid, FOREST_COLOR);
		PvDraw.textBold(g, font, bal, cx + innerW - balW, cy + headerTextMid, FOREST_COLOR);

		int ly = cy + headerH + 6;
		List<DungeonSnapshot.EssencePerk> perks = shop.perks();
		int colGap = 10;
		int colW = Math.max(80, (innerW - colGap) / 2);
		int perkRows = Math.max(1, (perks.size() + 1) / 2);
		int availPerkH = Math.max(font.lineHeight + 2, shopsBottom - ly);
		int rowH = Math.max(font.lineHeight + 2, Math.min(20, availPerkH / perkRows));
		for (int i = 0; i < perks.size(); i++) {
			DungeonSnapshot.EssencePerk perk = perks.get(i);
			int col = i % 2;
			int row = i / 2;
			int px = cx + col * (colW + colGap);
			int py = ly + row * rowH;
			if (py + font.lineHeight > shopsBottom) {
				break;
			}
			g.item(forestPerkIcon(perk.id()), px, py + Math.max(0, (rowH - 16) / 2));
			String right = perk.level() + "/" + perk.maxLevel();
			int rightW = font.width(right);
			int labelX = px + 16 + 3;
			String left = trim(font, perk.name(), Math.max(8, colW - 16 - 3 - rightW - 4));
			int textMid = Math.max(0, (rowH - font.lineHeight) / 2);
			int valueColor = perk.maxed() ? COLOR_MAXED : PvDraw.COLOR_TEXT;
			PvDraw.text(g, font, left, labelX, py + textMid, PvDraw.COLOR_MUTED);
			PvDraw.textRight(g, font, right, px + colW, py + textMid, valueColor);
		}

		int sepY = sectionSeparator(g, font, x, shopsBottom, w);
		drawMelodySection(g, font, cx, sepY, innerW, bottom);
	}

	private static ItemStack essenceIcon(String skyblockId) {
		ItemStack stack = SkyBlockItemFactory.iconStack(skyblockId == null ? "" : skyblockId);
		return stack == null || stack.isEmpty() ? new ItemStack(Items.FLOWERING_AZALEA_LEAVES) : stack;
	}

	private static ItemStack forestPerkIcon(String perkId) {
		if (perkId == null) {
			return new ItemStack(Items.PAPER);
		}
		return switch (perkId) {
			case "trapped" -> new ItemStack(Items.IRON_TRAPDOOR);
			case "axed" -> new ItemStack(Items.IRON_AXE);
			case "extreme_pressure" -> new ItemStack(Items.OBSIDIAN);
			case "lumberjack" -> new ItemStack(Items.OAK_LOG);
			case "tasty" -> new ItemStack(Items.APPLE);
			case "forest_training" -> new ItemStack(Items.BOOK);
			default -> new ItemStack(Items.PAPER);
		};
	}

	private void renderHotf(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my
	) {
		// Wider sides so "Whispers" / "Heart of the Forest" aren't truncated.
		int leftW = Math.max(128, Math.min(156, w / 5));
		int rightW = Math.max(136, Math.min(168, w * 22 / 100));
		int centerW = w - leftW - rightW - GAP * 2;
		if (centerW < 180) {
			int shrink = 180 - centerW;
			int takeLeft = Math.min(shrink / 2, Math.max(0, leftW - 118));
			int takeRight = Math.min(shrink - takeLeft, Math.max(0, rightW - 124));
			leftW -= takeLeft;
			rightW -= takeRight;
			centerW = w - leftW - rightW - GAP * 2;
		}
		int leftX = x;
		int centerX = x + leftW + GAP;
		int rightX = centerX + centerW + GAP;

		PvDraw.innerPanel(g, leftX, y, leftW, h);
		PvDraw.innerPanel(g, centerX, y, centerW, h);
		PvDraw.innerPanel(g, rightX, y, rightW, h);

		renderHotfLeft(g, font, leftX, y, leftW, h);
		renderHotfCenter(g, font, centerX, y, centerW, h, mx, my);
		renderHotfRight(g, font, rightX, y, rightW, h);
		this.maxScroll = 0;
		this.scroll = 0;
	}

	private void renderHotfLeft(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h) {
		int rx = x + PAD;
		int ry = y + PAD;
		int rw = w - PAD * 2;
		int bottom = y + h - PAD;

		PvDraw.text(g, font, "Whispers", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 3;
		ry = statLine(g, font, "Current", FormatUtil.commas(this.snapshot.whispers()),
			rx, ry, rw, PvDraw.COLOR_ACCENT) + 1;
		ry = statLine(g, font, "Spent", FormatUtil.commas(this.snapshot.whispersSpent()),
			rx, ry, rw, PvDraw.COLOR_MUTED) + 3;

		ry = sectionSeparator(g, font, x, ry, w);
		PvDraw.text(g, font, "Dailies", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 3;
		ry = statLine(g, font, "Trees cut", FormatUtil.commas(this.snapshot.dailyTreesCut()),
			rx, ry, rw, PvDraw.COLOR_TEXT) + 1;
		if (this.snapshot.dailyTreesDay() > 0) {
			ry = statLine(g, font, "Trees day", String.valueOf(this.snapshot.dailyTreesDay()),
				rx, ry, rw, PvDraw.COLOR_MUTED) + 1;
		}
		ry = statLine(g, font, "Gifts", FormatUtil.commas(this.snapshot.dailyGifts()),
			rx, ry, rw, PvDraw.COLOR_TEXT) + 2;

		if (!this.snapshot.dailyLogs().isEmpty() && ry + font.lineHeight < bottom) {
			PvDraw.text(g, font, "Logs cut", rx, ry, PvDraw.COLOR_MUTED);
			ry += font.lineHeight + 2;
			for (String log : this.snapshot.dailyLogs()) {
				if (ry + STAT_ROW > bottom) {
					break;
				}
				PvDraw.text(g, font, trim(font, pretty(log), rw), rx, ry, PvDraw.COLOR_TEXT);
				ry += STAT_ROW;
			}
			if (this.snapshot.dailyLogsDay() > 0 && ry + STAT_ROW <= bottom) {
				ry = statLine(g, font, "Logs day", String.valueOf(this.snapshot.dailyLogsDay()),
					rx, ry, rw, PvDraw.COLOR_MUTED) + 2;
			}
		}

		if (ry + STAT_ROW * 2 <= bottom) {
			ry = sectionSeparator(g, font, x, ry, w);
			PvDraw.text(g, font, "Daily effect", rx, ry, PvDraw.COLOR_MUTED);
			ry += font.lineHeight + 2;
			String effect = this.snapshot.dailyEffect().isBlank() ? "-" : pretty(this.snapshot.dailyEffect());
			wrapText(g, font, effect, rx, ry, rw, PvDraw.COLOR_GOLD);
			ry += font.lineHeight + 2;
			if (this.snapshot.dailyEffectChanged() > 0) {
				statLine(g, font, "Changed", String.valueOf(this.snapshot.dailyEffectChanged()),
					rx, ry, rw, PvDraw.COLOR_MUTED);
			}
		}
	}

	private void renderHotfCenter(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my
	) {
		List<ForagingHotfData.PerkDef> perks = ForagingHotfData.perks();
		if (perks.isEmpty()) {
			PvDraw.textCentered(g, font, "HOTF layout unavailable",
				x + w / 2, y + h / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED);
			return;
		}

		int cols = ForagingHotfData.maxX() + 1;
		int rows = ForagingHotfData.maxY() + 1;
		int innerW = w - PAD * 2;
		int innerH = h - PAD * 2;
		int baseIcon = 16;
		int basePad = 2;
		int baseCell = baseIcon + basePad * 2;
		int baseGap = 2;
		int baseGridW = cols * baseCell + Math.max(0, cols - 1) * baseGap;
		int baseGridH = rows * baseCell + Math.max(0, rows - 1) * baseGap;
		// Integer zoom only — fractional scale softens item textures.
		int zoom = 1;
		if (baseGridW > 0 && baseGridH > 0) {
			zoom = Math.max(1, Math.min(innerW / baseGridW, innerH / baseGridH));
			zoom = Math.min(3, zoom);
		}
		int cell = baseCell * zoom;
		int cellGap = baseGap * zoom;
		int iconDraw = baseIcon * zoom;
		int gridW = cols * cell + Math.max(0, cols - 1) * cellGap;
		int gridH = rows * cell + Math.max(0, rows - 1) * cellGap;
		int gridX = x + PAD + Math.max(0, (innerW - gridW) / 2);
		int gridY = y + PAD + Math.max(0, (innerH - gridH) / 2);

		g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
		for (ForagingHotfData.PerkDef perk : perks) {
			int cx = gridX + perk.x() * (cell + cellGap);
			int cy = gridY + perk.y() * (cell + cellGap);
			int level = this.snapshot.hotfNodeLevel(perk.id());
			PvDraw.fill(g, cx, cy, cell, cell, ITEM_SLOT_BG);
			g.outline(cx, cy, cell, cell, ITEM_SLOT_BORDER);
			ItemStack stack = hotfPerkIcon(perk, level);
			int ix = cx + Math.max(0, (cell - iconDraw) / 2);
			int iy = cy + Math.max(0, (cell - iconDraw) / 2);
			if (zoom == 1) {
				g.item(stack, ix, iy);
			} else {
				g.pose().pushMatrix();
				g.pose().translate(ix, iy);
				g.pose().scale(zoom, zoom);
				g.item(stack, 0, 0);
				g.pose().popMatrix();
			}
			List<PvTooltip.Line> tip = new ArrayList<>();
			tip.add(PvTooltip.Line.of(perk.name(), PvDraw.COLOR_TEXT));
			tip.add(PvTooltip.Line.of("Level " + level + " / " + perk.maxLevel(), PvDraw.COLOR_ACCENT));
			boolean enabled = this.snapshot.hotfNodeEnabled(perk.id());
			if (level > 0) {
				tip.add(PvTooltip.Line.of(enabled ? "Enabled" : "Disabled",
					enabled ? ENABLED : DISABLED));
			}
			if (perk.ability()) {
				tip.add(PvTooltip.Line.of("Ability", PvDraw.COLOR_GOLD));
			}
			this.zones.add(HoverZone.of(cx, cy, cell, cell, tip));
		}
		g.disableScissor();
	}

	/**
	 * Locked: pale oak button · unlocked: stripped oak · maxed: oak log.
	 * Locked ability: dead bush · unlocked/maxed ability: stripped/oak log.
	 */
	private static ItemStack hotfPerkIcon(ForagingHotfData.PerkDef perk, int level) {
		if (perk.ability()) {
			if (level <= 0) {
				return new ItemStack(Items.DEAD_BUSH);
			}
			return new ItemStack(level >= perk.maxLevel() ? Items.OAK_LOG : Items.STRIPPED_OAK_LOG);
		}
		if (level <= 0) {
			return new ItemStack(Items.PALE_OAK_BUTTON);
		}
		if (level >= perk.maxLevel()) {
			return new ItemStack(Items.OAK_LOG);
		}
		return new ItemStack(Items.STRIPPED_OAK_LOG);
	}

	private void renderHotfRight(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h) {
		int rx = x + PAD;
		int ry = y + PAD;
		int rw = w - PAD * 2;

		PvDraw.text(g, font, "Heart of the Forest", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 6;
		ry = statLine(g, font, "HOTF XP", FormatUtil.shortXp(this.snapshot.hotfXp()),
			rx, ry, rw, PvDraw.COLOR_ACCENT) + 4;
		ry = statLine(g, font, "Tokens spent", String.valueOf(this.snapshot.forestTokensSpent()),
			rx, ry, rw, PvDraw.COLOR_TEXT) + 4;
		if (this.snapshot.hotfLastResetMs() > 0L) {
			ry = statLine(g, font, "Last reset", formatAgo(this.snapshot.hotfLastResetMs()),
				rx, ry, rw, PvDraw.COLOR_MUTED) + 6;
		} else {
			ry += 6;
		}

		ry = sectionSeparator(g, font, x, ry, w);
		PvDraw.text(g, font, "Ability", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 4;
		String ability = this.snapshot.selectedAbility().isBlank()
			? "-"
			: ForagingHotfData.displayName(this.snapshot.selectedAbility());
		wrapText(g, font, ability, rx, ry, rw, PvDraw.COLOR_GOLD);
		ry += font.lineHeight + 8;

		if (this.snapshot.refundAbilityFree()) {
			statLine(g, font, "Refund", "Free", rx, ry, rw, ENABLED);
		}
	}

	private void renderHunting(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my
	) {
		int rightW = Math.max(180, w * 45 / 100);
		int leftW = w - rightW - GAP;
		PvDraw.innerPanel(g, x, y, leftW, h);
		PvDraw.innerPanel(g, x + leftW + GAP, y, rightW, h);

		drawShardsLeft(g, font, x, y, leftW, h, mx, my);
		drawToolkitRight(g, font, x + leftW + GAP, y, rightW, h, mx, my);
	}

	private void renderAttributeShards(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my
	) {
		int rightW = Math.max(210, w * 42 / 100);
		int leftW = w - rightW - GAP;
		PvDraw.innerPanel(g, x, y, leftW, h);
		PvDraw.innerPanel(g, x + leftW + GAP, y, rightW, h);

		List<ForagingSnapshot.AttributeShardRow> rows = this.snapshot.attributeShards();
		drawAttributeGrid(g, font, x, y, leftW, h, mx, my, rows);
		drawAttributeCostList(g, font, x + leftW + GAP, y, rightW, h, mx, my, rows);
		this.maxScroll = 0;
		this.scroll = 0;
	}

	private void drawAttributeGrid(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my,
		List<ForagingSnapshot.AttributeShardRow> rows
	) {
		int lx = x + PAD;
		int ly = y + PAD;
		int lw = w - PAD * 2;
		int bottom = y + h - PAD;

		PvDraw.text(g, font, "Attribute Shards", lx, ly, PvDraw.COLOR_MUTED);
		ly += font.lineHeight + 4;
		if (rows.isEmpty()) {
			PvDraw.textCentered(g, font, "No attribute data",
				x + w / 2, y + h / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED);
			this.attrGridMaxScroll = 0;
			this.attrGridScroll = 0;
			this.attrGridW = 0;
			this.attrGridH = 0;
			return;
		}

		int cols = Math.max(6, Math.min(12, (lw + SLOT_GAP) / (SLOT + SLOT_GAP)));
		int gridRows = (rows.size() + cols - 1) / cols;
		int contentH = gridRows * (SLOT + SLOT_GAP);
		this.attrGridX = x;
		this.attrGridY = ly;
		this.attrGridW = w;
		this.attrGridH = Math.max(0, bottom - ly);
		this.attrGridMaxScroll = Math.max(0, contentH - this.attrGridH);
		this.attrGridScroll = Math.min(this.attrGridScroll, this.attrGridMaxScroll);

		g.enableScissor(lx, this.attrGridY, lx + lw, this.attrGridY + this.attrGridH);
		for (int i = 0; i < rows.size(); i++) {
			ForagingSnapshot.AttributeShardRow row = rows.get(i);
			int col = i % cols;
			int r = i / cols;
			int bx = lx + col * (SLOT + SLOT_GAP);
			int by = ly + r * (SLOT + SLOT_GAP) - this.attrGridScroll;
			boolean hovered = mx >= bx && mx < bx + SLOT && my >= by && my < by + SLOT
				&& hit(mx, my, lx, this.attrGridY, lw, this.attrGridH);
			boolean locked = !row.unlocked();
			PvDraw.fill(g, bx, by, SLOT, SLOT, locked ? ITEM_SLOT_LOCKED_BG : ITEM_SLOT_BG);
			int border = locked
				? ITEM_SLOT_LOCKED_BORDER
				: (hovered ? PvDraw.COLOR_ACCENT : ITEM_SLOT_BORDER);
			g.outline(bx, by, SLOT, SLOT, border);
			ItemStack stack = attributeIcon(row);
			g.item(stack, bx + 1, by + 1);
			addClippedHover(bx, by, SLOT, SLOT, lx, this.attrGridY, lw, this.attrGridH, attributeTooltip(row));
		}
		g.disableScissor();
	}

	private void drawAttributeCostList(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my,
		List<ForagingSnapshot.AttributeShardRow> rows
	) {
		int rx = x + PAD;
		int ry = y + PAD;
		int rw = w - PAD * 2;
		int bottom = y + h - PAD;

		PvDraw.text(g, font, "Not maxed", rx, ry, PvDraw.COLOR_MUTED);

		List<ForagingSnapshot.AttributeShardRow> notMaxed = new ArrayList<>();
		for (ForagingSnapshot.AttributeShardRow row : rows) {
			if (row.level() < row.maxLevel()) {
				notMaxed.add(row);
			}
		}
		double totalCost = 0;
		for (ForagingSnapshot.AttributeShardRow row : notMaxed) {
			totalCost += shardMaxCost(row);
		}
		String totalText = totalCost > 0 ? FormatUtil.shortCoins(totalCost) : "—";
		PvDraw.textRight(g, font, totalText, rx + rw, ry, PvDraw.COLOR_GOLD);
		ry += font.lineHeight + 2;
		PvDraw.text(g, font, "Cheapest to max", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 2;
		ry = sectionSeparator(g, font, x, ry, w);

		notMaxed.sort(Comparator
			.comparingDouble(ForagingPage::shardMaxCost)
			.thenComparingInt(r -> Math.max(0, r.shardsForMax() - r.shardsOwned())));

		if (notMaxed.isEmpty()) {
			PvDraw.text(g, font, "All maxed", rx, ry, COLOR_MAXED);
			this.attrListMaxScroll = 0;
			this.attrListScroll = 0;
			this.attrListW = 0;
			this.attrListH = 0;
			return;
		}

		int rowH = Math.max(ICON + 2, font.lineHeight * 2 + 4);
		this.attrListX = x;
		this.attrListY = ry;
		this.attrListW = w;
		this.attrListH = Math.max(0, bottom - ry);
		int contentH = notMaxed.size() * rowH;
		this.attrListMaxScroll = Math.max(0, contentH - this.attrListH);
		this.attrListScroll = Math.min(this.attrListScroll, this.attrListMaxScroll);

		g.enableScissor(rx, this.attrListY, rx + rw, this.attrListY + this.attrListH);
		int yy = this.attrListY - this.attrListScroll;
		for (ForagingSnapshot.AttributeShardRow row : notMaxed) {
			int need = Math.max(0, row.shardsForMax() - row.shardsOwned());
			ItemStack stack = attributeIcon(row);
			g.item(stack, rx, yy);
			String qty = "x" + FormatUtil.commas(need);
			int qtyW = font.width(qty);
			int nameColor = rarityColor(row.rarity());
			PvDraw.text(g, font, trim(font, row.name(), Math.max(8, rw - ICON - 8 - qtyW)),
				rx + ICON + 4, yy, nameColor);
			PvDraw.textRight(g, font, qty, rx + rw, yy, PvDraw.COLOR_ACCENT);
			double cost = shardMaxCost(row);
			String costText = cost > 0 ? FormatUtil.shortCoins(cost) : "—";
			PvDraw.text(g, font, costText, rx + ICON + 4, yy + font.lineHeight + 1, PvDraw.COLOR_GOLD);
			addClippedHover(rx, yy, rw, rowH, rx, this.attrListY, rw, this.attrListH, attributeTooltip(row));
			yy += rowH;
		}
		g.disableScissor();
	}

	private static ItemStack attributeIcon(ForagingSnapshot.AttributeShardRow row) {
		ItemStack stack = SkyBlockItemFactory.iconStack(row.iconId());
		if (stack == null || stack.isEmpty() || stack.is(Items.BARRIER)) {
			stack = new ItemStack(Items.PRISMARINE_SHARD);
		}
		return stack;
	}

	private static List<PvTooltip.Line> attributeTooltip(ForagingSnapshot.AttributeShardRow row) {
		int need = Math.max(0, row.shardsForMax() - row.shardsOwned());
		double cost = shardMaxCost(row);
		List<PvTooltip.Line> tip = new ArrayList<>();
		tip.add(PvTooltip.Line.of(row.name(), rarityColor(row.rarity())));
		tip.add(PvTooltip.Line.of(pretty(row.rarity()), rarityColor(row.rarity())));
		tip.add(PvTooltip.Line.of("Level " + row.level() + " / " + row.maxLevel(), PvDraw.COLOR_ACCENT));
		tip.add(PvTooltip.Line.of(
			row.unlocked()
				? "Shards to max: " + FormatUtil.commas(need)
				: "Locked · " + FormatUtil.commas(row.shardsForMax()) + " shards to max",
			PvDraw.COLOR_MUTED
		));
		if (row.unlocked()) {
			tip.add(PvTooltip.Line.of("Owned: " + FormatUtil.commas(row.shardsOwned())
				+ " / " + FormatUtil.commas(row.shardsForMax()), PvDraw.COLOR_GOLD));
		}
		if (cost > 0) {
			tip.add(PvTooltip.Line.of("Cost to max: " + FormatUtil.shortCoins(cost), PvDraw.COLOR_GOLD));
		}
		return tip;
	}

	private static double shardMaxCost(ForagingSnapshot.AttributeShardRow row) {
		int need = Math.max(0, row.shardsForMax() - row.shardsOwned());
		if (need <= 0) {
			return 0;
		}
		double unit = ItemPricer.price(row.priceId());
		if (unit <= 0) {
			unit = ItemPricer.price(row.iconId());
		}
		if (unit <= 0) {
			String icon = row.iconId();
			int semi = icon == null ? -1 : icon.indexOf(';');
			if (semi > 0) {
				unit = ItemPricer.price(icon.substring(0, semi));
			}
		}
		return unit > 0 ? unit * need : 0;
	}

	private static int rarityColor(String rarity) {
		if (rarity == null) {
			return PvDraw.COLOR_TEXT;
		}
		return switch (rarity.toUpperCase(Locale.ROOT)) {
			case "COMMON" -> 0xFFFFFFFF;
			case "UNCOMMON" -> 0xFF55FF55;
			case "RARE" -> 0xFF5555FF;
			case "EPIC" -> 0xFFAA00AA;
			case "LEGENDARY" -> 0xFFFFAA00;
			case "MYTHIC" -> 0xFFFF55FF;
			case "DIVINE" -> 0xFF55FFFF;
			default -> PvDraw.COLOR_TEXT;
		};
	}

	private void drawShardsLeft(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my
	) {
		int lx = x + PAD;
		int ly = y + PAD;
		int lw = w - PAD * 2;
		int bottom = y + h - PAD;

		ly = drawBar(g, font, "Hunting", String.valueOf(this.snapshot.huntingLevel()),
			this.snapshot.huntingFill(), this.snapshot.huntingMaxed(), BAR_HUNT,
			this.snapshot.huntingHover(), lx, ly, lw) + BAR_AFTER + 2;

		ly = statLine(g, font, "Fused", FormatUtil.commas(this.snapshot.fusedShards()),
			lx, ly, lw, PvDraw.COLOR_GOLD);
		ly = sectionSeparator(g, font, x, ly, w);

		if (!this.snapshot.huntStats().isEmpty()) {
			PvDraw.text(g, font, "Shard Drops", lx, ly, PvDraw.COLOR_MUTED);
			ly += font.lineHeight + 8;
			for (Map.Entry<String, Long> e : this.snapshot.huntStats().entrySet()) {
				if (ly + STAT_ROW > bottom) {
					break;
				}
				String label = pretty(e.getKey().replace("shard_", "").replace("_hunts", ""));
				ly = statLine(g, font, label, FormatUtil.commas(e.getValue()),
					lx, ly, lw, PvDraw.COLOR_TEXT) + 2;
			}
		}
		this.maxScroll = 0;
		this.scroll = 0;
	}

	private void drawToolkitRight(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my
	) {
		int rx = x + PAD;
		int ry = y + PAD;
		int rw = w - PAD * 2;
		int bottom = y + h - PAD;

		PvDraw.text(g, font, "Hunting toolkit", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 3;
		ry = statLine(g, font, "Unlocked", this.snapshot.toolkitUnlocked() ? "Yes" : "No",
			rx, ry, rw, this.snapshot.toolkitUnlocked() ? ENABLED : DISABLED) + 3;

		List<ForagingSnapshot.ToolkitSlot> slots = this.snapshot.toolkitSlots();
		if (slots.isEmpty()) {
			PvDraw.text(g, font, "No slots", rx, ry, PvDraw.COLOR_MUTED);
			return;
		}

		String lastGroup = "";
		int cols = Math.max(3, Math.min(5, (rw + SLOT_GAP) / (SLOT + SLOT_GAP)));
		int col = 0;
		int rowY = ry;
		for (ForagingSnapshot.ToolkitSlot slot : slots) {
			if (!slot.group().equals(lastGroup)) {
				if (!lastGroup.isEmpty()) {
					rowY += SLOT + SLOT_GAP + 2;
					col = 0;
				}
				if (rowY + font.lineHeight + SLOT > bottom) {
					break;
				}
				PvDraw.text(g, font, pretty(slot.group()), rx, rowY, PvDraw.COLOR_MUTED);
				rowY += font.lineHeight + 2;
				lastGroup = slot.group();
			}
			if (col >= cols) {
				col = 0;
				rowY += SLOT + SLOT_GAP;
			}
			if (rowY + SLOT > bottom) {
				break;
			}
			int bx = rx + col * (SLOT + SLOT_GAP);
			int by = rowY;
			boolean hovered = mx >= bx && mx < bx + SLOT && my >= by && my < by + SLOT;
			PvDraw.fill(g, bx, by, SLOT, SLOT, ITEM_SLOT_BG);
			g.outline(bx, by, SLOT, SLOT, slot.inUse()
				? PvDraw.COLOR_GOLD
				: (hovered ? PvDraw.COLOR_ACCENT : ITEM_SLOT_BORDER));
			InventorySnapshot.Slot item = slot.item();
			boolean filled = item != null && !item.isEmpty();
			ItemStack stack = ItemStack.EMPTY;
			if (filled) {
				stack = SkyBlockItemFactory.toStack(item);
				if (stack == null || stack.isEmpty()) {
					stack = SkyBlockItemFactory.iconStack(item.id());
				}
				if (stack == null || stack.isEmpty()) {
					stack = new ItemStack(Items.TRIPWIRE_HOOK);
				}
				g.item(stack, bx + 1, by + 1);
			}
			List<PvTooltip.Line> tip = new ArrayList<>();
			tip.add(PvTooltip.Line.of(pretty(slot.group()) + " #" + (slot.index() + 1), PvDraw.COLOR_TEXT));
			if (filled) {
				String name = item.displayName() == null || item.displayName().isBlank()
					? pretty(item.id()) : item.displayName();
				tip.add(PvTooltip.Line.of(name, PvDraw.COLOR_ACCENT));
			} else {
				tip.add(PvTooltip.Line.of("Empty", PvDraw.COLOR_MUTED));
			}
			if (slot.inUse()) {
				tip.add(PvTooltip.Line.of("In use", PvDraw.COLOR_GOLD));
			}
			this.zones.add(HoverZone.of(bx, by, SLOT, SLOT, tip));
			col++;
		}
	}

	private int drawBar(
		GuiGraphicsExtractor g, Font font, String label, String value, float fill, boolean maxed,
		int color, String hover, int x, int y, int w
	) {
		String shown = fitValue(font, label, value == null ? "" : value, w);
		PvDraw.labeledBar(g, font, trim(font, label, Math.max(24, w - font.width(shown) - 8)),
			shown, fill, x, y, w, color, maxed);
		int bottom = y + font.lineHeight + BAR_LABEL_GAP + PvDraw.BAR_HEIGHT;
		if (hover != null && !hover.isBlank()) {
			this.zones.add(HoverZone.of(x, y, w, bottom - y, List.of(PvTooltip.Line.of(hover, PvDraw.COLOR_TEXT))));
		}
		return bottom;
	}

	private static int sectionSeparator(GuiGraphicsExtractor g, Font font, int panelX, int y, int panelW) {
		// Center the rule in the gap between previous and next content (matches mining).
		int visualBottom = y - Math.max(0, STAT_ROW - font.lineHeight);
		int pad = SEP_GAP / 2;
		int lineY = visualBottom + pad;
		int lineInset = PAD + 4;
		int lineW = Math.max(0, panelW - lineInset * 2);
		if (lineW > 0) {
			PvDraw.fill(g, panelX + lineInset, lineY, lineW, 1, 0x33FFFFFF);
		}
		return lineY + 1 + pad;
	}

	private static int statLine(
		GuiGraphicsExtractor g, Font font, String label, String value, int x, int y, int w, int valueColor
	) {
		String r = value == null ? "" : value;
		int leftMax = Math.max(8, w - font.width(r) - 6);
		PvDraw.text(g, font, trim(font, label, leftMax), x, y, PvDraw.COLOR_MUTED);
		PvDraw.textRight(g, font, r, x + w, y, valueColor);
		return y + STAT_ROW;
	}

	private static void wrapText(GuiGraphicsExtractor g, Font font, String text, int x, int y, int w, int color) {
		String t = text == null || text.isBlank() ? "-" : text;
		PvDraw.text(g, font, trim(font, t, w), x, y, color);
	}

	private void drawHover(GuiGraphicsExtractor g, Font font, int mx, int my, int screenW, int screenH) {
		for (HoverZone zone : this.zones) {
			if (mx >= zone.x && mx < zone.x + zone.w && my >= zone.y && my < zone.y + zone.h) {
				PvTooltip.drawStyled(g, font, zone.lines, mx, my, screenW, screenH);
				return;
			}
		}
	}

	/** Only the visible (scissor-clipped) portion of a slot/row is hoverable. */
	private void addClippedHover(
		int bx, int by, int bw, int bh,
		int clipX, int clipY, int clipW, int clipH,
		List<PvTooltip.Line> tip
	) {
		int x0 = Math.max(bx, clipX);
		int y0 = Math.max(by, clipY);
		int x1 = Math.min(bx + bw, clipX + clipW);
		int y1 = Math.min(by + bh, clipY + clipH);
		if (x1 > x0 && y1 > y0) {
			this.zones.add(HoverZone.of(x0, y0, x1 - x0, y1 - y0, tip));
		}
	}

	private static boolean hit(double mx, double my, int x, int y, int w, int h) {
		return w > 0 && h > 0 && mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private static float easeInOutCubic(float t) {
		return t < 0.5F ? 4F * t * t * t : 1F - (float) Math.pow(-2F * t + 2F, 3) / 2F;
	}

	private static String formatRaceMs(long ms) {
		if (ms <= 0L) {
			return "-";
		}
		long totalSec = ms / 1000L;
		long mins = totalSec / 60L;
		long secs = totalSec % 60L;
		long remMs = ms % 1000L;
		if (mins > 0) {
			return mins + ":" + String.format(Locale.ROOT, "%02d.%03d", secs, remMs);
		}
		return secs + "." + String.format(Locale.ROOT, "%03d", remMs) + "s";
	}

	private static String formatAgo(long ms) {
		if (ms <= 0L) {
			return "-";
		}
		long ago = Math.max(0L, System.currentTimeMillis() - ms);
		long mins = ago / 60_000L;
		if (mins < 60L) {
			return mins + "m ago";
		}
		long hours = mins / 60L;
		if (hours < 48L) {
			return hours + "h ago";
		}
		return (hours / 24L) + "d ago";
	}

	private static String shortAttr(String name) {
		if (name == null) {
			return "";
		}
		String[] parts = name.split(" ");
		if (parts.length <= 2) {
			return name;
		}
		return parts[0] + " " + parts[parts.length - 1];
	}

	/** Native 16×16 fish-family icon (no fractional downscale). */
	private static void drawFishFamilyIcon(GuiGraphicsExtractor g, String id, int x, int y) {
		Identifier texture = id == null || id.isBlank() ? null : SkyBlockItemFactory.customIcon(id);
		if (texture != null) {
			int tex = SkyBlockItemFactory.customIconSize(id);
			g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, 16, 16, tex, tex, tex, tex);
			return;
		}
		ItemStack stack = id == null || id.isBlank() ? ItemStack.EMPTY : SkyBlockItemFactory.iconStack(id);
		if (stack == null || stack.isEmpty()) {
			stack = new ItemStack(Items.TROPICAL_FISH);
		}
		g.item(stack, x, y);
	}

	private static void drawSkyblockIcon(GuiGraphicsExtractor g, String id, int x, int y, int size) {
		Identifier texture = id == null || id.isBlank() ? null : SkyBlockItemFactory.customIcon(id);
		if (texture != null) {
			int tex = SkyBlockItemFactory.customIconSize(id);
			g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, size, size, tex, tex, tex, tex);
			return;
		}
		ItemStack stack = id == null || id.isBlank() ? ItemStack.EMPTY : SkyBlockItemFactory.iconStack(id);
		if (stack == null || stack.isEmpty() || stack.is(Items.PAPER)) {
			String upper = id == null ? "" : id.toUpperCase(Locale.ROOT);
			if (upper.contains("TREE")) {
				stack = new ItemStack(Items.OAK_SAPLING);
			} else {
				stack = new ItemStack(Items.TROPICAL_FISH);
			}
		}
		if (size == 16) {
			g.item(stack, x, y);
			return;
		}
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		float s = size / 16f;
		g.pose().scale(s, s);
		g.item(stack, 0, 0);
		g.pose().popMatrix();
	}

	private static String pretty(String id) {
		if (id == null || id.isBlank()) {
			return "";
		}
		String[] parts = id.replace('-', '_').split("_");
		StringBuilder sb = new StringBuilder();
		for (String part : parts) {
			if (part.isEmpty()) {
				continue;
			}
			if (sb.length() > 0) {
				sb.append(' ');
			}
			sb.append(Character.toUpperCase(part.charAt(0)));
			if (part.length() > 1) {
				sb.append(part.substring(1).toLowerCase(Locale.ROOT));
			}
		}
		return sb.toString();
	}

	private static String fitValue(Font font, String label, String value, int w) {
		int max = Math.max(8, w - font.width(label) - 10);
		return trim(font, value, max);
	}

	private static String trim(Font font, String text, int maxW) {
		if (text == null) {
			return "";
		}
		if (font.width(text) <= maxW) {
			return text;
		}
		String ellipsis = "...";
		int ew = font.width(ellipsis);
		if (maxW <= ew) {
			return ellipsis;
		}
		StringBuilder sb = new StringBuilder(text);
		while (sb.length() > 0 && font.width(sb.toString()) + ew > maxW) {
			sb.setLength(sb.length() - 1);
		}
		return sb + ellipsis;
	}

	private record HoverZone(int x, int y, int w, int h, List<PvTooltip.Line> lines) {
		static HoverZone of(int x, int y, int w, int h, List<PvTooltip.Line> lines) {
			return new HoverZone(x, y, w, h, lines);
		}
	}
}
