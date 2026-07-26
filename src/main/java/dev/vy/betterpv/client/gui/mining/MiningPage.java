package dev.vy.betterpv.client.gui.mining;

import com.mojang.blaze3d.platform.InputConstants;
import dev.vy.betterpv.client.data.ColeWeight;
import dev.vy.betterpv.client.data.DungeonSnapshot;
import dev.vy.betterpv.client.data.FormatUtil;
import dev.vy.betterpv.client.data.MiningHotmData;
import dev.vy.betterpv.client.data.MiningSnapshot;
import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.PvTooltip;
import dev.vy.betterpv.client.gui.inventories.SkyBlockItemFactory;
import dev.vy.betterpv.client.gui.nav.PvSubTab;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Mining: Overview (Garden-style two panels) + HOTM (powders | tree | forge). */
public final class MiningPage {
	private static final int PAD = 6;
	private static final int GAP = 6;
	private static final int BAR_LABEL_GAP = 2;
	private static final int BAR_AFTER = 4;
	private static final int STAT_ROW = 12;
	private static final int CRYSTAL_ICON = 14;
	private static final int SIDE_MIN = 118;
	private static final int SEP_GAP = 10;
	private static final int BAR_HOTM = 0xFF5B8CFF;
	private static final int BAR_MINING = 0xFFAAAAAA;
	private static final int BAR_MITHRIL = 0xFF55AA55;
	private static final int BAR_GEM = 0xFFCC55CC;
	private static final int BAR_GLACITE = 0xFF55CCCC;
	private static final int BAR_CORPSE = 0xFF6B9BD1;
	private static final int PLACED = 0xFF55FF55;
	private static final int FOUND = 0xFFE8C84A;
	private static final int MISSING = 0xFF9A9AAC;
	private static final int CORPSE_LAPIS = 0xFF5555FF;
	private static final int CORPSE_UMBER = 0xFFC4A35A;
	private static final int CORPSE_TUNGSTEN = 0xFFB0B0B8;
	private static final int CORPSE_VANGUARD = 0xFF55FFFF;
	private static final int FLIP_MS = 480;
	private static final int PANEL_HOVER = 0x0AFFFFFF;
	private static final int GOLD_COLOR = 0xFFFFAA00;
	private static final int DIAMOND_COLOR = 0xFF55FFFF;
	private static final int ESSENCE_HEADER_ICON = 16;
	private static final int ESSENCE_PERK_ICON = 10;
	private static final int COLOR_MAXED = 0xFF7CFF9A;

	private MiningSnapshot snapshot = MiningSnapshot.empty();
	private PvSubTab lastSub;
	private int scroll;
	private int maxScroll;
	private int scrollTop;
	private int scrollH;
	private int hotmUnlockedScrollTop;
	private int hotmUnlockedScrollH;
	private int hotmUnlockedMaxScroll;
	private final List<HoverZone> zones = new ArrayList<>();

	private boolean crystalsEssenceFace;
	private long crystalsFlipStartMs;
	private boolean crystalsFlipTarget;
	private int crystalsHitX;
	private int crystalsHitY;
	private int crystalsHitW;
	private int crystalsHitH;

	public void apply(MiningSnapshot snapshot) {
		this.snapshot = snapshot == null ? MiningSnapshot.empty() : snapshot;
		this.scroll = 0;
		this.zones.clear();
		this.crystalsEssenceFace = false;
		this.crystalsFlipStartMs = 0L;
		MiningHotmData.ensureLoaded();
	}

	public MiningSnapshot snapshot() {
		return this.snapshot;
	}

	public boolean mouseClicked(double mx, double my) {
		if (this.lastSub == PvSubTab.MINING_HOTM || this.lastSub == PvSubTab.MINING_GLACITE) {
			return false;
		}
		if (this.crystalsHitW <= 0 || this.crystalsHitH <= 0) {
			return false;
		}
		if (mx < this.crystalsHitX || mx >= this.crystalsHitX + this.crystalsHitW
			|| my < this.crystalsHitY || my >= this.crystalsHitY + this.crystalsHitH) {
			return false;
		}
		if (this.crystalsFlipStartMs != 0L) {
			return true;
		}
		this.crystalsFlipTarget = !this.crystalsEssenceFace;
		this.crystalsFlipStartMs = System.currentTimeMillis();
		return true;
	}

	public void render(
		GuiGraphicsExtractor g, Font font, PvSubTab sub,
		int x, int y, int w, int h, int mouseX, int mouseY, int screenW, int screenH
	) {
		this.zones.clear();
		PvSubTab mode = sub == null ? PvSubTab.MINING_OVERVIEW : sub;
		if (mode != this.lastSub) {
			this.lastSub = mode;
			this.scroll = 0;
			if (mode != PvSubTab.MINING_OVERVIEW) {
				this.crystalsEssenceFace = false;
				this.crystalsFlipStartMs = 0L;
				this.crystalsHitW = 0;
				this.crystalsHitH = 0;
			}
		}

		switch (mode) {
			case MINING_HOTM -> renderHotm(g, font, x, y, w, h, mouseX, mouseY);
			case MINING_GLACITE -> renderGlacite(g, font, x, y, w, h, mouseX, mouseY);
			default -> renderOverview(g, font, x, y, w, h, mouseX, mouseY);
		}
		drawHover(g, font, mouseX, mouseY, screenW, screenH);
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY, PvSubTab sub) {
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

	private void renderOverview(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my
	) {
		int rightW = Math.max(200, w * 55 / 100);
		int leftW = w - rightW - GAP;
		PvDraw.innerPanel(g, x, y, leftW, h);

		int lx = x + PAD;
		int ly = y + PAD;
		int lw = leftW - PAD * 2;
		int leftBottom = y + h - PAD;

		ly = drawBar(g, font, "HOTM", String.valueOf(this.snapshot.hotmLevel()),
			this.snapshot.hotmFill(), this.snapshot.hotmMaxed(), BAR_HOTM,
			this.snapshot.hotmHover(), lx, ly, lw, mx, my) + BAR_AFTER;
		ly = drawBar(g, font, "Mining", String.valueOf(this.snapshot.miningLevel()),
			this.snapshot.miningFill(), this.snapshot.miningMaxed(), BAR_MINING,
			this.snapshot.miningHover(), lx, ly, lw, mx, my) + BAR_AFTER + 2;

		ColeWeight.Result cw = this.snapshot.coleWeight();
		String cwValue = FormatUtil.oneDecimal(cw.total());
		int cwTop = ly;
		ly = statLine(g, font, "ColeWeight", cwValue, lx, ly, lw, PvDraw.COLOR_GOLD) + 2;
		this.zones.add(HoverZone.coleWeight(lx, cwTop, lw, STAT_ROW));

		ly = drawPowdersBlock(g, font, lx, ly, lw);
		ly = sectionSeparator(g, font, x, ly, leftW);

		ly = statLine(g, font, "Tokens spent", String.valueOf(this.snapshot.tokensSpent()),
			lx, ly, lw, PvDraw.COLOR_TEXT) + 1;
		ly = statLine(g, font, "Ability", trim(font, this.snapshot.selectedAbilityName(), lw / 2),
			lx, ly, lw, PvDraw.COLOR_ACCENT) + 1;
		ly = statLine(g, font, "Sky Mall", trim(font, this.snapshot.skyMallEffect(), lw / 2),
			lx, ly, lw, PvDraw.COLOR_GOLD);
		ly = sectionSeparator(g, font, x, ly, leftW);

		if (this.snapshot.commissionMilestone() > 0) {
			ly = statLine(g, font, "Commissions", "Tier " + this.snapshot.commissionMilestone(),
				lx, ly, lw, PvDraw.COLOR_TEXT);
			ly = sectionSeparator(g, font, x, ly, leftW);
		}

		drawCrystalsPanel(g, font, x + leftW + GAP, y, rightW, h, mx, my);
	}

	private void drawCrystalsPanel(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my
	) {
		this.crystalsHitX = x;
		this.crystalsHitY = y;
		this.crystalsHitW = w;
		this.crystalsHitH = h;

		boolean hovered = mx >= x && mx < x + w && my >= y && my < y + h;
		float flipProgress = 0F;
		boolean animating = this.crystalsFlipStartMs != 0L;
		if (animating) {
			flipProgress = Math.min(1F, (System.currentTimeMillis() - this.crystalsFlipStartMs) / (float) FLIP_MS);
			if (flipProgress >= 1F) {
				this.crystalsEssenceFace = this.crystalsFlipTarget;
				this.crystalsFlipStartMs = 0L;
				animating = false;
				flipProgress = 0F;
			}
		}
		float eased = animating ? easeInOutCubic(flipProgress) : 0F;
		float angle = eased * (float) Math.PI;
		boolean showEssence = animating
			? (Math.cos(angle) < 0.0 ? this.crystalsFlipTarget : this.crystalsEssenceFace)
			: this.crystalsEssenceFace;
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
			this.maxScroll = 0;
			drawMiningEssenceFace(g, font, x, y, w, h);
		} else {
			drawCrystalsFace(g, font, x, y, w, h);
		}

		g.pose().popMatrix();
	}

	private void drawCrystalsFace(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h) {
		int rx = x + PAD;
		int ry = y + PAD;
		int rw = w - PAD * 2;
		int bottom = y + h - PAD;
		PvDraw.text(g, font, "Crystals", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 4;
		List<MiningSnapshot.Crystal> crystals = this.snapshot.crystals();
		if (crystals.isEmpty()) {
			PvDraw.text(g, font, "No crystal data", rx, ry, PvDraw.COLOR_MUTED);
			this.maxScroll = 0;
			return;
		}

		int cols = 2;
		int colGap = 10;
		int colW = Math.max(40, (rw - colGap * (cols - 1)) / cols);
		int cellH = Math.max(STAT_ROW, CRYSTAL_ICON) + 4;
		int rows = (crystals.size() + cols - 1) / cols;
		int gridH = rows * cellH;

		this.scrollTop = ry;
		this.scrollH = Math.max(0, bottom - ry - STAT_ROW - 8);
		this.maxScroll = Math.max(0, gridH - this.scrollH);
		this.scroll = Math.min(this.scroll, this.maxScroll);

		g.enableScissor(rx, this.scrollTop, rx + rw, this.scrollTop + this.scrollH);
		for (int i = 0; i < crystals.size(); i++) {
			int col = i % cols;
			int row = i / cols;
			int cx = rx + col * (colW + colGap);
			int cy = ry + row * cellH - this.scroll;
			drawCrystalCell(g, font, crystals.get(i), cx, cy, colW, cellH);
		}
		g.disableScissor();

		int nucY = this.maxScroll <= 0
			? ry + gridH + 8
			: this.scrollTop + this.scrollH + 6;
		statLine(g, font, "Nucleus runs", FormatUtil.commas(this.snapshot.nucleusRuns()),
			rx, nucY, rw, PvDraw.COLOR_ACCENT);
	}

	/** Gold / Diamond essence shops stacked full-width (names fit; rows fill each half). */
	private void drawMiningEssenceFace(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h) {
		int pad = 6;
		int cx = x + pad;
		int cy = y + pad;
		int innerW = w - pad * 2;
		int bottom = y + h - pad;
		int innerH = Math.max(0, bottom - cy);
		int sectionGap = 8;
		int halfH = Math.max(40, (innerH - sectionGap) / 2);

		int headerH = Math.max(ESSENCE_HEADER_ICON, font.lineHeight + 2);
		int goldTop = cy;
		int goldBottom = cy + halfH;
		int diamondTop = goldBottom + sectionGap;
		int diamondBottom = bottom;

		int goldPerks = Math.max(1, this.snapshot.goldShop().perks().size());
		int diamondPerks = Math.max(1, this.snapshot.diamondShop().perks().size());
		int goldBody = Math.max(0, goldBottom - goldTop - headerH - 4);
		int diamondBody = Math.max(0, diamondBottom - diamondTop - headerH - 4);
		int goldRowH = Math.max(font.lineHeight + 2, Math.max(ESSENCE_PERK_ICON + 3, goldBody / goldPerks));
		int diamondRowH = Math.max(font.lineHeight + 2, Math.max(ESSENCE_PERK_ICON + 3, diamondBody / diamondPerks));

		drawMiningEssenceColumn(
			g, font, this.snapshot.goldShop(), cx, goldTop, innerW, headerH, goldRowH, goldBottom, GOLD_COLOR
		);

		int sepY = goldBottom + sectionGap / 2;
		PvDraw.fill(g, cx, sepY, innerW, 1, PvDraw.COLOR_BORDER);

		drawMiningEssenceColumn(
			g, font, this.snapshot.diamondShop(), cx, diamondTop, innerW, headerH, diamondRowH, diamondBottom, DIAMOND_COLOR
		);
	}

	private void drawMiningEssenceColumn(
		GuiGraphicsExtractor g,
		Font font,
		DungeonSnapshot.EssenceShop shop,
		int x,
		int y,
		int w,
		int headerH,
		int rowH,
		int bottom,
		int headerColor
	) {
		int headerIcon = ESSENCE_HEADER_ICON;
		int perkIcon = ESSENCE_PERK_ICON;
		int gap = 3;
		int headerLabelX = x + headerIcon + gap;
		int headerTextMid = Math.max(0, (headerH - font.lineHeight) / 2);
		int headerIconMid = Math.max(0, (headerH - headerIcon) / 2);

		drawItemIcon(g, essenceIcon(shop.iconId()), x, y + headerIconMid, headerIcon);
		String name = shop.name();
		String bal = FormatUtil.commas(shop.balance());
		int balW = PvDraw.widthBold(font, bal);
		int nameMax = Math.max(8, w - (headerLabelX - x) - balW - 4);
		PvDraw.textBold(g, font, trim(font, name, nameMax), headerLabelX, y + headerTextMid, headerColor);
		PvDraw.textBold(g, font, bal, x + w - balW, y + headerTextMid, headerColor);

		int perkLabelX = x + perkIcon + gap;
		int perkTextMid = Math.max(0, (rowH - font.lineHeight) / 2);
		int perkIconMid = Math.max(0, (rowH - perkIcon) / 2);
		int ly = y + headerH + 4;
		for (DungeonSnapshot.EssencePerk perk : shop.perks()) {
			if (ly + font.lineHeight > bottom) {
				break;
			}
			drawItemIcon(g, miningEssencePerkIcon(perk.id()), x, ly + perkIconMid, perkIcon);
			String right = perk.level() + "/" + perk.maxLevel();
			int rightW = font.width(right);
			String left = trim(font, perk.name(), Math.max(8, w - (perkLabelX - x) - rightW - 4));
			int valueColor = perk.maxed() ? COLOR_MAXED : PvDraw.COLOR_TEXT;
			PvDraw.text(g, font, left, perkLabelX, ly + perkTextMid, PvDraw.COLOR_MUTED);
			PvDraw.textRight(g, font, right, x + w, ly + perkTextMid, valueColor);
			ly += rowH;
		}
	}

	private static ItemStack essenceIcon(String skyblockId) {
		ItemStack stack = SkyBlockItemFactory.iconStack(skyblockId == null ? "" : skyblockId);
		return stack == null || stack.isEmpty() ? new ItemStack(Items.PLAYER_HEAD) : stack;
	}

	private static ItemStack miningEssencePerkIcon(String perkId) {
		if (perkId == null) {
			return new ItemStack(Items.PAPER);
		}
		return switch (perkId) {
			case "heart_of_gold" -> new ItemStack(Items.GOLDEN_APPLE);
			case "treasures_of_the_earth" -> new ItemStack(Items.CHEST);
			case "dwarven_training" -> new ItemStack(Items.IRON_PICKAXE);
			case "unbreaking" -> new ItemStack(Items.ANVIL);
			case "eager_miner" -> new ItemStack(Items.GOLDEN_PICKAXE);
			case "midas_lure" -> new ItemStack(Items.FISHING_ROD);
			case "radiant_fisher" -> new ItemStack(Items.COD);
			case "diamond_in_the_rough" -> new ItemStack(Items.DIAMOND);
			case "rhinestone_infusion" -> new ItemStack(Items.AMETHYST_SHARD);
			case "under_pressure" -> new ItemStack(Items.OBSIDIAN);
			case "high_roller" -> new ItemStack(Items.GOLD_BLOCK);
			case "return_to_sender" -> new ItemStack(Items.ARROW);
			default -> new ItemStack(Items.PAPER);
		};
	}

	private static void drawItemIcon(GuiGraphicsExtractor g, ItemStack icon, int x, int y, int size) {
		if (icon == null || icon.isEmpty()) {
			return;
		}
		if (size == 16) {
			g.item(icon, x, y);
			return;
		}
		float scale = size / 16f;
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.item(icon, 0, 0);
		g.pose().popMatrix();
	}

	private static float easeInOutCubic(float t) {
		return t < 0.5F
			? 4F * t * t * t
			: 1F - (float) Math.pow(-2F * t + 2F, 3) / 2F;
	}

	private void drawCrystalCell(
		GuiGraphicsExtractor g, Font font, MiningSnapshot.Crystal c, int x, int y, int w, int cellH
	) {
		boolean collected = c.found();
		int nameColor = collected ? crystalColor(c.id()) : mixMuted(crystalColor(c.id()));
		int stateColor = c.placed() ? PLACED : (c.found() ? FOUND : MISSING);
		String state = prettyState(c.state());

		int iconY = y + Math.max(0, (cellH - CRYSTAL_ICON) / 2);
		drawCrystalIcon(g, c.id(), x, iconY, CRYSTAL_ICON);

		int textX = x + CRYSTAL_ICON + 4;
		int textW = Math.max(8, w - CRYSTAL_ICON - 4);
		int textY = y + Math.max(0, (cellH - font.lineHeight) / 2);
		String name = trim(font, c.name(), Math.max(8, textW - font.width(state) - 6));
		PvDraw.text(g, font, name, textX, textY, nameColor);
		PvDraw.textRight(g, font, state, textX + textW, textY, stateColor);
	}

	/** Flawless gemstone player-head cubes (classic NBT skulls). */
	private static void drawCrystalIcon(GuiGraphicsExtractor g, String crystalApiId, int x, int y, int size) {
		ItemStack icon = SkyBlockItemFactory.gemstoneHead(crystalApiId);
		if (icon == null || icon.isEmpty()) {
			icon = new ItemStack(Items.PLAYER_HEAD);
		}
		if (size == 16) {
			g.item(icon, x, y);
			return;
		}
		float scale = size / 16f;
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.item(icon, 0, 0);
		g.pose().popMatrix();
	}

	private static int crystalColor(String apiId) {
		String id = apiId == null ? "" : apiId.toLowerCase(Locale.ROOT);
		if (id.contains("jade")) {
			return 0xFF55FF55;
		}
		if (id.contains("amber")) {
			return 0xFFFFAA00;
		}
		if (id.contains("amethyst")) {
			return 0xFFCC66FF;
		}
		if (id.contains("sapphire")) {
			return 0xFF55FFFF;
		}
		if (id.contains("topaz")) {
			return 0xFFFFE55A;
		}
		if (id.contains("jasper")) {
			return 0xFFFF55FF;
		}
		if (id.contains("ruby")) {
			return 0xFFFF5555;
		}
		if (id.contains("aquamarine")) {
			return 0xFF5599FF;
		}
		if (id.contains("citrine")) {
			return 0xFFFF5555;
		}
		if (id.contains("peridot")) {
			return 0xFF88FF44;
		}
		if (id.contains("onyx")) {
			return 0xFFB0B0B8;
		}
		if (id.contains("opal")) {
			return 0xFFE8E8F0;
		}
		return PvDraw.COLOR_ACCENT;
	}

	private static int mixMuted(int argb) {
		int a = (argb >>> 24) & 0xFF;
		int r = (argb >>> 16) & 0xFF;
		int g = (argb >>> 8) & 0xFF;
		int b = argb & 0xFF;
		r = (r + 0x9A) / 2;
		g = (g + 0x9A) / 2;
		b = (b + 0xAC) / 2;
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	private void renderGlacite(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my
	) {
		int rightW = Math.max(200, w * 55 / 100);
		int leftW = w - rightW - GAP;
		PvDraw.innerPanel(g, x, y, leftW, h);
		PvDraw.innerPanel(g, x + leftW + GAP, y, rightW, h);

		int lx = x + PAD;
		int ly = y + PAD;
		int lw = leftW - PAD * 2;

		PvDraw.text(g, font, "Glacite", lx, ly, PvDraw.COLOR_MUTED);
		ly += font.lineHeight + 4;

		ly = statLine(g, font, "Mineshafts entered", FormatUtil.commas(this.snapshot.mineshaftsEntered()),
			lx, ly, lw, PvDraw.COLOR_TEXT);
		ly = sectionSeparator(g, font, x, ly, leftW);
		ly = statLine(g, font, "Fossil dust", FormatUtil.commas(this.snapshot.fossilDust()),
			lx, ly, lw, BAR_GLACITE) + 1;

		List<String> fossils = this.snapshot.fossilsDonated();
		String fossilCount = String.valueOf(fossils.size());
		int fossilTop = ly;
		ly = statLine(g, font, "Fossils donated", fossilCount, lx, ly, lw, PvDraw.COLOR_ACCENT);
		if (!fossils.isEmpty()) {
			List<PvTooltip.Line> tip = new ArrayList<>();
			tip.add(PvTooltip.Line.of("Fossils donated", PvDraw.COLOR_MUTED));
			for (String fossil : fossils) {
				tip.add(PvTooltip.Line.of(title(fossil), PLACED));
			}
			this.zones.add(HoverZone.of(lx, fossilTop, lw, STAT_ROW, tip));
		}
		ly = sectionSeparator(g, font, x, ly, leftW);

		MiningSnapshot.CorpseCounts counts = this.snapshot.corpses();
		PvDraw.text(g, font, "Corpses looted", lx, ly, PvDraw.COLOR_MUTED);
		ly += font.lineHeight + 3;
		ly = coloredLabelStat(g, font, "Lapis", FormatUtil.commas(counts.lapis()),
			lx, ly, lw, CORPSE_LAPIS, PvDraw.COLOR_TEXT) + 1;
		ly = coloredLabelStat(g, font, "Umber", FormatUtil.commas(counts.umber()),
			lx, ly, lw, CORPSE_UMBER, PvDraw.COLOR_TEXT) + 1;
		ly = coloredLabelStat(g, font, "Tungsten", FormatUtil.commas(counts.tungsten()),
			lx, ly, lw, CORPSE_TUNGSTEN, PvDraw.COLOR_TEXT) + 1;
		ly = coloredLabelStat(g, font, "Vanguard", FormatUtil.commas(counts.vanguard()),
			lx, ly, lw, CORPSE_VANGUARD, PvDraw.COLOR_TEXT) + 1;
		ly = statLine(g, font, "Total", FormatUtil.commas(counts.total()), lx, ly, lw, PvDraw.COLOR_ACCENT);
		ly = sectionSeparator(g, font, x, ly, leftW);

		int current = this.snapshot.corpseMilestone();
		String tierLabel = current <= 0 ? "None" : ("Tier " + current + (current >= 7 ? " (max)" : ""));
		statLine(g, font, "Milestone", tierLabel, lx, ly, lw, PvDraw.COLOR_ACCENT);

		renderCorpseMilestones(g, font, x + leftW + GAP, y, rightW, h, mx, my);
	}

	/**
	 * Horizontal rule centered between previous and next text.
	 * {@code y} is the cursor after a STAT_ROW-based line; corrects for unused row slack.
	 */
	private static int sectionSeparator(GuiGraphicsExtractor g, Font font, int panelX, int y, int panelW) {
		int visualBottom = y - Math.max(0, STAT_ROW - font.lineHeight);
		int pad = SEP_GAP / 2;
		int lineY = visualBottom + pad;
		int lineInset = PAD + 6;
		int lineW = Math.max(0, panelW - lineInset * 2);
		if (lineW > 0) {
			PvDraw.fill(g, panelX + lineInset, lineY, lineW, 1, PvDraw.COLOR_BORDER);
		}
		return lineY + 1 + pad;
	}

	private void renderCorpseMilestones(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my
	) {
		int rx = x + PAD;
		int ry = y + PAD;
		int rw = w - PAD * 2;

		PvDraw.text(g, font, "Corpse milestones", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 4;

		MiningSnapshot.CorpseCounts counts = this.snapshot.corpses();
		int current = this.snapshot.corpseMilestone();

		int gridTop = ry;
		int gridH = y + h - PAD - gridTop;
		this.scrollTop = gridTop;
		this.scrollH = gridH;

		List<MiningSnapshot.CorpseMilestone> tiers = MiningSnapshot.CORPSE_MILESTONES;
		int rowH = Math.max(barRowH(font), gridH / Math.max(1, tiers.size()));
		this.maxScroll = Math.max(0, tiers.size() * rowH - gridH);
		this.scroll = Math.min(this.scroll, this.maxScroll);

		g.enableScissor(rx, gridTop, rx + rw, gridTop + gridH);
		int yy = gridTop - this.scroll;
		for (int i = 0; i < tiers.size(); i++) {
			MiningSnapshot.CorpseMilestone tier = tiers.get(i);
			MiningSnapshot.CorpseMilestone prev = i == 0 ? null : tiers.get(i - 1);
			boolean done = current >= tier.tier();
			float fill = done ? 1f : tier.fill(counts, prev);
			String value = done ? "Done" : requirementShort(tier, counts);
			String hover = corpseHover(tier, counts, done);
			yy = drawBar(g, font, "Tier " + tier.tier(), value, fill, done, BAR_CORPSE, hover,
				rx, yy, rw, mx, my) + Math.max(BAR_AFTER, rowH - barRowH(font) + BAR_AFTER);
		}
		g.disableScissor();
	}

	private void renderHotm(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my
	) {
		int sideW = Math.max(SIDE_MIN, Math.min(140, w / 5));
		int centerW = w - sideW * 2 - GAP * 2;
		if (centerW < 160) {
			sideW = Math.max(100, (w - GAP * 2 - 160) / 2);
			centerW = w - sideW * 2 - GAP * 2;
		}

		int leftX = x;
		int centerX = x + sideW + GAP;
		int rightX = centerX + centerW + GAP;

		PvDraw.innerPanel(g, leftX, y, sideW, h);
		PvDraw.innerPanel(g, centerX, y, centerW, h);
		PvDraw.innerPanel(g, rightX, y, sideW, h);

		renderHotmLeft(g, font, leftX, y, sideW, h, mx, my);
		renderHotmTree(g, font, centerX, y, centerW, h, mx, my);
		renderHotmForge(g, font, rightX, y, sideW, h);
		// Unlocked list no longer scrolls; clear hitbox so wheel doesn't get stuck there.
		this.maxScroll = 0;
		this.scroll = 0;
	}

	private void renderHotmLeft(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my
	) {
		int rx = x + PAD;
		int ry = y + PAD;
		int rw = w - PAD * 2;
		int bottom = y + h - PAD;

		ry = drawBar(g, font, "HOTM", String.valueOf(this.snapshot.hotmLevel()),
			this.snapshot.hotmFill(), this.snapshot.hotmMaxed(), BAR_HOTM,
			this.snapshot.hotmHover(), rx, ry, rw, mx, my) + BAR_AFTER;
		ry = drawBar(g, font, "Mining", String.valueOf(this.snapshot.miningLevel()),
			this.snapshot.miningFill(), this.snapshot.miningMaxed(), BAR_MINING,
			this.snapshot.miningHover(), rx, ry, rw, mx, my) + BAR_AFTER + 2;

		ry = drawPowdersBlock(g, font, rx, ry, rw) + 2;

		MiningHotmData.PerkDef cotm = MiningHotmData.perk("core_of_the_mountain");
		int cotmLevel = this.snapshot.nodeLevel("core_of_the_mountain");
		int cotmMax = cotm == null ? 10 : cotm.maxLevel();
		PvDraw.text(g, font, "Core of the Mountain", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 2;
		int cotmColor = cotmLevel >= cotmMax ? PLACED : PvDraw.COLOR_ACCENT;
		String cotmValue = cotmLevel + " / " + cotmMax;
		PvDraw.text(g, font, cotmValue, rx, ry, cotmColor);
		this.zones.add(HoverZone.of(rx, ry - font.lineHeight - 2, rw, font.lineHeight + STAT_ROW,
			List.of(
				PvTooltip.Line.of("Core of the Mountain", PvDraw.COLOR_TEXT),
				PvTooltip.Line.of("Level " + cotmLevel + " / " + cotmMax, PvDraw.COLOR_ACCENT)
			)));
		ry += font.lineHeight + 4;

		PvDraw.text(g, font, "Ability", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 2;
		wrapText(g, font, this.snapshot.selectedAbilityName(), rx, ry, rw, PvDraw.COLOR_ACCENT);
		ry += font.lineHeight + 4;

		List<UnlockedNode> unlocked = unlockedNodes();
		PvDraw.text(g, font, "Unlocked (" + unlocked.size() + ")", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 3;

		this.hotmUnlockedScrollTop = 0;
		this.hotmUnlockedScrollH = 0;
		this.hotmUnlockedMaxScroll = 0;
		this.maxScroll = 0;
		this.scroll = 0;

		if (unlocked.isEmpty()) {
			PvDraw.text(g, font, "None", rx, ry, PvDraw.COLOR_MUTED);
			return;
		}

		int availH = Math.max(font.lineHeight, bottom - ry);
		int natural = STAT_ROW + 1;
		int fit = Math.max(8, availH / Math.max(1, unlocked.size()));
		int rowH = Math.min(natural, fit);
		float scale = rowH < font.lineHeight ? rowH / (float) font.lineHeight : 1.0f;
		int yy = ry;
		for (UnlockedNode node : unlocked) {
			int valueColor = node.ability() ? PvDraw.COLOR_GOLD
				: (node.maxed() ? PLACED : PvDraw.COLOR_TEXT);
			String right = node.level() + "/" + node.maxLevel();
			int rightW = Math.max(1, Math.round(font.width(right) * scale));
			String left = trim(font, node.name(), Math.max(8, rw - rightW - 6));
			if (scale >= 0.99f) {
				PvDraw.text(g, font, left, rx, yy, valueColor);
				PvDraw.textRight(g, font, right, rx + rw, yy, PvDraw.COLOR_MUTED);
			} else {
				PvDraw.textScaled(g, font, left, rx, yy, valueColor, scale);
				PvDraw.textScaled(g, font, right, rx + rw - rightW, yy, PvDraw.COLOR_MUTED, scale);
			}
			List<PvTooltip.Line> tip = new ArrayList<>();
			tip.add(PvTooltip.Line.of(node.name(), PvDraw.COLOR_TEXT));
			tip.add(PvTooltip.Line.of("Level " + node.level() + " / " + node.maxLevel(), PvDraw.COLOR_ACCENT));
			if (node.ability()) {
				tip.add(PvTooltip.Line.of("Pickaxe Ability", PvDraw.COLOR_GOLD));
			}
			this.zones.add(HoverZone.of(rx, yy, rw, rowH, tip));
			yy += rowH;
		}
	}

	private List<UnlockedNode> unlockedNodes() {
		List<UnlockedNode> out = new ArrayList<>();
		for (MiningHotmData.PerkDef perk : MiningHotmData.perks()) {
			if ("core_of_the_mountain".equals(perk.id())) {
				continue;
			}
			int level = this.snapshot.nodeLevel(perk.id());
			if (level <= 0) {
				continue;
			}
			out.add(new UnlockedNode(
				perk.id(),
				perk.name(),
				level,
				perk.maxLevel(),
				perk.ability(),
				level >= perk.maxLevel()
			));
		}
		out.sort(Comparator
			.comparing(UnlockedNode::ability).reversed()
			.thenComparing(UnlockedNode::name, String.CASE_INSENSITIVE_ORDER));
		return out;
	}

	private void renderHotmForge(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h) {
		int rx = x + PAD;
		int ry = y + PAD;
		int rw = w - PAD * 2;
		PvDraw.text(g, font, "Forge", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 3;
		drawForgeList(g, font, rx, ry, rw, y + h - PAD);
		// Keep unlocked-node scroll from the left rail; forge is short.
	}

	private int drawForgeList(GuiGraphicsExtractor g, Font font, int x, int y, int w, int bottom) {
		List<MiningSnapshot.ForgeProcess> forge = this.snapshot.forge();
		if (forge.isEmpty()) {
			PvDraw.text(g, font, "No items forging", x, y, PvDraw.COLOR_MUTED);
			return y + STAT_ROW;
		}
		int ly = y;
		for (MiningSnapshot.ForgeProcess p : forge) {
			if (ly + STAT_ROW > bottom) {
				break;
			}
			String left = "Slot " + p.slot();
			String right = trim(font, p.name(), Math.max(24, w - font.width(left) - 8));
			ly = statLine(g, font, left, right, x, ly, w, PvDraw.COLOR_TEXT) + 1;
			String ago = forgeAgo(p.startTimeMs());
			if (!ago.isBlank() && ly + STAT_ROW <= bottom) {
				ly = statLine(g, font, "", ago, x, ly, w, PvDraw.COLOR_MUTED) + 1;
			}
		}
		return ly;
	}

	/** Header + three powder totals (no bars). Returns bottom Y. */
	private int drawPowdersBlock(GuiGraphicsExtractor g, Font font, int x, int y, int w) {
		PvDraw.text(g, font, "Powders", x, y, PvDraw.COLOR_MUTED);
		int ly = y + font.lineHeight + 3;
		ly = coloredLabelStat(g, font, "Mithril", FormatUtil.shortXp(this.snapshot.mithril().total()),
			x, ly, w, BAR_MITHRIL, PvDraw.COLOR_TEXT) + 1;
		this.zones.add(HoverZone.of(x, ly - STAT_ROW, w, STAT_ROW, powderHover("Mithril", this.snapshot.mithril())));
		ly = coloredLabelStat(g, font, "Gemstone", FormatUtil.shortXp(this.snapshot.gemstone().total()),
			x, ly, w, BAR_GEM, PvDraw.COLOR_TEXT) + 1;
		this.zones.add(HoverZone.of(x, ly - STAT_ROW, w, STAT_ROW, powderHover("Gemstone", this.snapshot.gemstone())));
		ly = coloredLabelStat(g, font, "Glacite", FormatUtil.shortXp(this.snapshot.glacite().total()),
			x, ly, w, BAR_GLACITE, PvDraw.COLOR_TEXT) + 1;
		this.zones.add(HoverZone.of(x, ly - STAT_ROW, w, STAT_ROW, powderHover("Glacite", this.snapshot.glacite())));
		return ly;
	}

	private static List<PvTooltip.Line> powderHover(String name, MiningSnapshot.Powder powder) {
		List<PvTooltip.Line> tip = new ArrayList<>();
		tip.add(PvTooltip.Line.of(name + " powder", PvDraw.COLOR_TEXT));
		tip.add(PvTooltip.Line.of("Total: " + FormatUtil.commas(powder.total()), PvDraw.COLOR_GOLD));
		tip.add(PvTooltip.Line.of("Available: " + FormatUtil.commas(powder.available()), PvDraw.COLOR_MUTED));
		tip.add(PvTooltip.Line.of("Spent: " + FormatUtil.commas(powder.spent()), PvDraw.COLOR_MUTED));
		return tip;
	}

	private static List<PvTooltip.Line> coleWeightHover(ColeWeight.Result weight, boolean shiftDetails) {
		List<PvTooltip.Line> tip = new ArrayList<>();
		tip.add(PvTooltip.Line.of("ColeWeight: " + FormatUtil.oneDecimal(weight.total()), PvDraw.COLOR_GOLD));
		tip.add(PvTooltip.Line.blank());

		Map<String, List<ColeWeight.Line>> byCat = weight.byCategory();
		ColeWeight.Line miningXp = firstLine(byCat.get("experience"), "Mining Experience");
		if (miningXp != null) {
			tip.add(PvTooltip.Line.of(
				"Mining XP: " + FormatUtil.oneDecimal(miningXp.weight()),
				PvDraw.COLOR_TEXT
			));
			tip.add(PvTooltip.Line.blank());
		}

		List<ColeWeight.Line> powders = byCat.get("powder");
		if (powders != null && !powders.isEmpty()) {
			tip.add(PvTooltip.Line.of("Powder", PvDraw.COLOR_ACCENT));
			tip.add(PvTooltip.Line.of(
				"Mithril: " + FormatUtil.oneDecimal(lineWeight(powders, "Mithril Powder")),
				BAR_MITHRIL
			));
			tip.add(PvTooltip.Line.of(
				"Gemstone: " + FormatUtil.oneDecimal(lineWeight(powders, "Gemstone Powder")),
				BAR_GEM
			));
			tip.add(PvTooltip.Line.of(
				"Glacite: " + FormatUtil.oneDecimal(lineWeight(powders, "Glacite Powder")),
				BAR_GLACITE
			));
			tip.add(PvTooltip.Line.blank());
		}

		List<ColeWeight.Line> collections = byCat.get("collection");
		double collectionTotal = sumWeight(collections);
		tip.add(PvTooltip.Line.of("Collection", PvDraw.COLOR_ACCENT));
		if (shiftDetails && collections != null && !collections.isEmpty()) {
			List<ColeWeight.Line> shown = new ArrayList<>(collections);
			shown.sort((a, b) -> Double.compare(b.weight(), a.weight()));
			for (ColeWeight.Line line : shown) {
				tip.add(PvTooltip.Line.of(
					line.label() + ": " + FormatUtil.oneDecimal(line.weight()),
					PvDraw.COLOR_MUTED
				));
			}
		} else {
			tip.add(PvTooltip.Line.of(
				"Overall: " + FormatUtil.oneDecimal(collectionTotal),
				PvDraw.COLOR_TEXT
			));
		}
		tip.add(PvTooltip.Line.blank());

		List<ColeWeight.Line> misc = byCat.get("miscellaneous");
		tip.add(PvTooltip.Line.of("Misc", PvDraw.COLOR_ACCENT));
		tip.add(PvTooltip.Line.of(
			"Worm kills: " + FormatUtil.oneDecimal(lineWeight(misc, "Worm Kills")),
			PvDraw.COLOR_MUTED
		));
		tip.add(PvTooltip.Line.of(
			"Nuc runs: " + FormatUtil.oneDecimal(lineWeight(misc, "Nucleus Runs")),
			PvDraw.COLOR_MUTED
		));

		if (!shiftDetails) {
			tip.add(PvTooltip.Line.blank());
			tip.add(PvTooltip.Line.of("Hold L-shift for details", PvDraw.COLOR_MUTED));
		}
		return tip;
	}

	private static ColeWeight.Line firstLine(List<ColeWeight.Line> lines, String label) {
		if (lines == null) {
			return null;
		}
		for (ColeWeight.Line line : lines) {
			if (label.equals(line.label())) {
				return line;
			}
		}
		return lines.isEmpty() ? null : lines.getFirst();
	}

	private static double lineWeight(List<ColeWeight.Line> lines, String label) {
		if (lines == null) {
			return 0;
		}
		for (ColeWeight.Line line : lines) {
			if (label.equals(line.label())) {
				return line.weight();
			}
		}
		return 0;
	}

	private static double sumWeight(List<ColeWeight.Line> lines) {
		if (lines == null || lines.isEmpty()) {
			return 0;
		}
		double sum = 0;
		for (ColeWeight.Line line : lines) {
			sum += line.weight();
		}
		return sum;
	}

	private void renderHotmTree(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my
	) {
		List<MiningHotmData.PerkDef> perks = MiningHotmData.perks();
		if (perks.isEmpty()) {
			PvDraw.textCentered(g, font, "HOTM layout unavailable",
				x + w / 2, y + h / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED);
			this.maxScroll = 0;
			return;
		}

		int cols = MiningHotmData.maxX() + 1;
		int rows = MiningHotmData.maxY() + 1;
		int innerW = w - PAD * 2;
		int innerH = h - PAD * 2;
		int cellGap = 1;
		int cellByW = cols <= 0 ? 16 : Math.max(1, (innerW - Math.max(0, cols - 1) * cellGap) / cols);
		int cellByH = rows <= 0 ? 16 : Math.max(1, (innerH - Math.max(0, rows - 1) * cellGap) / rows);
		int cell = Math.min(16, Math.min(cellByW, cellByH));
		int gridW = cols * cell + (cols - 1) * cellGap;
		int gridH = rows * cell + (rows - 1) * cellGap;
		int gridX = x + PAD + Math.max(0, (innerW - gridW) / 2);
		int gridY = y + PAD + Math.max(0, (innerH - gridH) / 2);

		this.scrollTop = y + PAD;
		this.scrollH = innerH;

		int icon = Math.max(1, Math.min(16, cell));

		for (MiningHotmData.PerkDef perk : perks) {
			int cx = gridX + perk.x() * (cell + cellGap);
			int cy = gridY + perk.y() * (cell + cellGap);
			int level = this.snapshot.nodeLevel(perk.id());

			ItemStack stack = perkIcon(perk, level);
			int ix = cx + (cell - icon) / 2;
			int iy = cy + (cell - icon) / 2;
			float scale = icon / 16f;
			if (scale != 1f) {
				g.pose().pushMatrix();
				g.pose().translate(ix, iy);
				g.pose().scale(scale, scale);
				g.item(stack, 0, 0);
				g.pose().popMatrix();
			} else {
				g.item(stack, ix, iy);
			}

			List<PvTooltip.Line> tip = new ArrayList<>();
			tip.add(PvTooltip.Line.of(perk.name(), PvDraw.COLOR_TEXT));
			tip.add(PvTooltip.Line.of("Level " + level + " / " + perk.maxLevel(), PvDraw.COLOR_ACCENT));
			if (perk.ability()) {
				tip.add(PvTooltip.Line.of("Pickaxe Ability", PvDraw.COLOR_GOLD));
			}
			if (perk.powder() != null && !perk.powder().isBlank() && !perk.powder().startsWith("(")) {
				tip.add(PvTooltip.Line.of(prettyPowder(perk.powder()) + " powder", powderColor(perk.powder())));
			}
			this.zones.add(HoverZone.of(cx, cy, cell, cell, tip));
		}
	}

	/**
	 * Abilities: locked coal block, unlocked diamond block.
	 * CotM: diamond block only when maxed, otherwise coal block.
	 * Other perks: locked coal, unlocked emerald, maxed diamond.
	 */
	private static ItemStack perkIcon(MiningHotmData.PerkDef perk, int level) {
		if ("core_of_the_mountain".equals(perk.id())) {
			return new ItemStack(level >= perk.maxLevel() ? Items.DIAMOND_BLOCK : Items.COAL_BLOCK);
		}
		if (perk.ability()) {
			return new ItemStack(level > 0 ? Items.DIAMOND_BLOCK : Items.COAL_BLOCK);
		}
		if (level <= 0) {
			return new ItemStack(Items.COAL);
		}
		if (level >= perk.maxLevel()) {
			return new ItemStack(Items.DIAMOND);
		}
		return new ItemStack(Items.EMERALD);
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
			this.zones.add(HoverZone.of(x, y, w, bottom - y, List.of(PvTooltip.Line.of(hover, PvDraw.COLOR_TEXT))));
		}
		return bottom;
	}

	private static int barRowH(Font font) {
		return font.lineHeight + BAR_LABEL_GAP + PvDraw.BAR_HEIGHT + BAR_AFTER;
	}

	private static int coloredLabelStat(
		GuiGraphicsExtractor g, Font font, String label, String value,
		int x, int y, int w, int labelColor, int valueColor
	) {
		String r = value == null ? "" : value;
		int leftMax = Math.max(8, w - font.width(r) - 6);
		PvDraw.text(g, font, trim(font, label, leftMax), x, y, labelColor);
		PvDraw.textRight(g, font, r, x + w, y, valueColor);
		return y + STAT_ROW;
	}

	private static int statLine(
		GuiGraphicsExtractor g, Font font, String label, String value, int x, int y, int w, int valueColor
	) {
		return coloredLabelStat(g, font, label, value, x, y, w, PvDraw.COLOR_MUTED, valueColor);
	}

	private static void wrapText(GuiGraphicsExtractor g, Font font, String text, int x, int y, int w, int color) {
		String t = text == null || text.isBlank() ? "-" : text;
		if (font.width(t) <= w) {
			PvDraw.text(g, font, t, x, y, color);
			return;
		}
		PvDraw.text(g, font, trim(font, t, w), x, y, color);
	}

	private void drawHover(GuiGraphicsExtractor g, Font font, int mx, int my, int screenW, int screenH) {
		for (HoverZone zone : this.zones) {
			if (mx >= zone.x && mx < zone.x + zone.w && my >= zone.y && my < zone.y + zone.h) {
				List<PvTooltip.Line> tip = zone.coleWeight
					? coleWeightHover(this.snapshot.coleWeight(), leftShiftDown())
					: zone.lines;
				PvTooltip.drawStyled(g, font, tip, mx, my, screenW, screenH);
				return;
			}
		}
	}

	private static String prettyState(String state) {
		if (state == null || state.isBlank()) {
			return "-";
		}
		return switch (state.toUpperCase(Locale.ROOT)) {
			case "PLACED" -> "Placed";
			case "FOUND" -> "Found";
			case "NOT_FOUND" -> "Missing";
			default -> title(state);
		};
	}

	private static String prettyPowder(String powder) {
		if (powder == null || powder.isBlank()) {
			return "";
		}
		return title(powder);
	}

	private static int powderColor(String powder) {
		if (powder == null) {
			return PvDraw.COLOR_MUTED;
		}
		return switch (powder.toUpperCase(Locale.ROOT)) {
			case "MITHRIL" -> BAR_MITHRIL;
			case "GEMSTONE" -> BAR_GEM;
			case "GLACITE" -> BAR_GLACITE;
			default -> PvDraw.COLOR_MUTED;
		};
	}

	private static String requirementShort(MiningSnapshot.CorpseMilestone tier, MiningSnapshot.CorpseCounts counts) {
		List<String> parts = new ArrayList<>();
		if (tier.needLapis() > 0) {
			parts.add(counts.lapis() + "/" + tier.needLapis() + " L");
		}
		if (tier.needUmber() > 0) {
			parts.add(counts.umber() + "/" + tier.needUmber() + " U");
		}
		if (tier.needTungsten() > 0) {
			parts.add(counts.tungsten() + "/" + tier.needTungsten() + " T");
		}
		if (tier.needVanguard() > 0) {
			parts.add(counts.vanguard() + "/" + tier.needVanguard() + " V");
		}
		return parts.isEmpty() ? "-" : String.join(" ", parts);
	}

	private static String corpseHover(
		MiningSnapshot.CorpseMilestone tier, MiningSnapshot.CorpseCounts counts, boolean done
	) {
		if (done) {
			return "Tier " + tier.tier() + " complete";
		}
		StringBuilder sb = new StringBuilder("Need for tier ").append(tier.tier()).append(':');
		if (tier.needLapis() > 0) {
			sb.append(" Lapis ").append(counts.lapis()).append('/').append(tier.needLapis());
		}
		if (tier.needUmber() > 0) {
			sb.append(" Umber ").append(counts.umber()).append('/').append(tier.needUmber());
		}
		if (tier.needTungsten() > 0) {
			sb.append(" Tungsten ").append(counts.tungsten()).append('/').append(tier.needTungsten());
		}
		if (tier.needVanguard() > 0) {
			sb.append(" Vanguard ").append(counts.vanguard()).append('/').append(tier.needVanguard());
		}
		return sb.toString();
	}

	private static String forgeAgo(long startMs) {
		if (startMs <= 0L) {
			return "";
		}
		long ago = Math.max(0L, System.currentTimeMillis() - startMs);
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

	private static String title(String raw) {
		String[] parts = raw.replace('-', '_').split("_");
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

	private static boolean leftShiftDown() {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.getWindow() == null) {
			return false;
		}
		return InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_LSHIFT);
	}

	private record UnlockedNode(
		String id, String name, int level, int maxLevel, boolean ability, boolean maxed
	) {
	}

	private record HoverZone(int x, int y, int w, int h, List<PvTooltip.Line> lines, boolean coleWeight) {
		static HoverZone of(int x, int y, int w, int h, List<PvTooltip.Line> lines) {
			return new HoverZone(x, y, w, h, lines, false);
		}

		static HoverZone coleWeight(int x, int y, int w, int h) {
			return new HoverZone(x, y, w, h, List.of(), true);
		}
	}
}
