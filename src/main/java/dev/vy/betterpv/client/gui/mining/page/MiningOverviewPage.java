package dev.vy.betterpv.client.gui.mining.page;

import dev.vy.betterpv.client.data.ColeWeight;
import dev.vy.betterpv.client.data.FormatUtil;
import dev.vy.betterpv.client.data.MiningSnapshot;
import dev.vy.betterpv.client.data.PlayerStatsSnapshot;
import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.PvTooltip;
import dev.vy.betterpv.client.gui.inventories.SkyBlockItemFactory;
import dev.vy.betterpv.client.gui.mining.MiningUi;
import dev.vy.betterpv.client.gui.mining.MiningUi.HoverZone;
import dev.vy.betterpv.client.gui.overview.SkillStatRows;
import dev.vy.betterpv.client.gui.overview.SummaryCards;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class MiningOverviewPage {
	private static final int CRYSTAL_ICON = 14;
	private static final String[] SKILL_STATS = { "mining_speed", "mining_fortune", "pristine", "mining_spread" };
	private static final List<String> STAT_EXCLUSIONS = List.of(
		"Excludes held tool, set bonuses,",
		"temporary buffs and location bonuses."
	);

	private int scroll;
	private int maxScroll;
	private int scrollTop;
	private int scrollH;

	public void resetScroll() {
		this.scroll = 0;
	}

	public void onEnter() {
		this.scroll = 0;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
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
		GuiGraphicsExtractor g, Font font, MiningSnapshot snapshot, PlayerStatsSnapshot stats, List<HoverZone> zones,
		int x, int y, int w, int h, int mx, int my
	) {
		int rightW = Math.max(200, w * 55 / 100);
		int leftW = w - rightW - MiningUi.GAP;
		PvDraw.innerPanel(g, x, y, leftW, h);

		int lx = x + MiningUi.PAD;
		int ly = y + MiningUi.PAD;
		int lw = leftW - MiningUi.PAD * 2;

		ly = MiningUi.drawBar(g, font, "HOTM", String.valueOf(snapshot.hotmLevel()),
			snapshot.hotmFill(), snapshot.hotmMaxed(), MiningUi.BAR_HOTM,
			snapshot.hotmHover(), lx, ly, lw, mx, my, zones) + MiningUi.BAR_AFTER;
		ly = MiningUi.drawBar(g, font, "Mining", String.valueOf(snapshot.miningLevel()),
			snapshot.miningFill(), snapshot.miningMaxed(), MiningUi.BAR_MINING,
			snapshot.miningHover(), lx, ly, lw, mx, my, zones) + MiningUi.BAR_AFTER + 2;

		ColeWeight.Result cw = snapshot.coleWeight();
		String cwValue = FormatUtil.oneDecimal(cw.total());
		int cwTop = ly;
		ly = MiningUi.statLine(g, font, "ColeWeight", cwValue, lx, ly, lw, PvDraw.COLOR_GOLD) + 2;
		zones.add(HoverZone.coleWeight(lx, cwTop, lw, MiningUi.STAT_ROW));

		ly = MiningUi.drawPowdersBlock(g, font, snapshot, lx, ly, lw, zones);
		ly = MiningUi.sectionSeparator(g, font, x, ly, leftW);

		ly = MiningUi.statLine(g, font, "Tokens spent", String.valueOf(snapshot.tokensSpent()),
			lx, ly, lw, PvDraw.COLOR_TEXT) + 1;
		ly = MiningUi.statLine(g, font, "Ability", MiningUi.trim(font, snapshot.selectedAbilityName(), lw / 2),
			lx, ly, lw, PvDraw.COLOR_ACCENT) + 1;
		ly = MiningUi.statLine(g, font, "Sky Mall", MiningUi.trim(font, snapshot.skyMallEffect(), lw / 2),
			lx, ly, lw, PvDraw.COLOR_GOLD);
		ly = MiningUi.sectionSeparator(g, font, x, ly, leftW);

		if (snapshot.commissionMilestone() > 0) {
			ly = MiningUi.statLine(g, font, "Commissions", "Tier " + snapshot.commissionMilestone(),
				lx, ly, lw, PvDraw.COLOR_TEXT) + 1;
		}

		if (snapshot.miningFiestaOres() > 0L) {
			ly = MiningUi.statLine(g, font, "Fiesta ores",
				FormatUtil.commas(snapshot.miningFiestaOres()), lx, ly, lw, PvDraw.COLOR_GOLD);
		}

		int bottom = y + h - MiningUi.PAD;
		if (ly + MiningUi.STAT_ROW * 2 <= bottom) {
			ly = MiningUi.sectionSeparator(g, font, x, ly, leftW);
			for (String id : SKILL_STATS) {
				if (ly + MiningUi.STAT_ROW > bottom) {
					break;
				}
				SummaryCards.Stat stat = SkillStatRows.row(stats, id, STAT_EXCLUSIONS);
				zones.add(HoverZone.of(lx, ly, lw, MiningUi.STAT_ROW, stat.tip()));
				ly = MiningUi.coloredLabelStat(g, font, stat.label(), stat.value(), lx, ly, lw,
					stat.labelColor(), stat.valueColor()) + 1;
			}
		}

		int rightX = x + leftW + MiningUi.GAP;
		PvDraw.innerPanel(g, rightX, y, rightW, h);
		drawCrystalsFace(g, font, snapshot, zones, rightX, y, rightW, h);
	}

	private void drawCrystalsFace(
		GuiGraphicsExtractor g, Font font, MiningSnapshot snapshot, List<HoverZone> zones,
		int x, int y, int w, int h
	) {
		int rx = x + MiningUi.PAD;
		int ry = y + MiningUi.PAD;
		int rw = w - MiningUi.PAD * 2;
		int bottom = y + h - MiningUi.PAD;
		PvDraw.text(g, font, "Crystals", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 4;
		List<MiningSnapshot.Crystal> crystals = snapshot.crystals();
		if (crystals.isEmpty()) {
			PvDraw.text(g, font, "No crystal data", rx, ry, PvDraw.COLOR_MUTED);
			this.maxScroll = 0;
			return;
		}

		int cols = 2;
		int colGap = 10;
		int colW = Math.max(40, (rw - colGap * (cols - 1)) / cols);
		int cellH = Math.max(MiningUi.STAT_ROW, CRYSTAL_ICON) + 4;
		int rows = (crystals.size() + cols - 1) / cols;
		int gridH = rows * cellH;

		boolean hollows = snapshot.goblinKingQuests() > 0
			|| snapshot.jungleTempleOpen()
			|| snapshot.precursorTalked();
		int hollowsReserve = hollows
			? (snapshot.goblinKingQuests() > 0 ? MiningUi.STAT_ROW + 1 : 0)
				+ MiningUi.STAT_ROW + 1
				+ MiningUi.STAT_ROW
				+ 6
			: 0;

		this.scrollTop = ry;
		this.scrollH = Math.max(0, bottom - ry - MiningUi.STAT_ROW - 8 - hollowsReserve);
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
		long derived = snapshot.nucleusRuns();
		long api = snapshot.apiNucleusRuns();
		MiningUi.statLine(g, font, "Nucleus runs", FormatUtil.commas(derived),
			rx, nucY, rw, PvDraw.COLOR_ACCENT);
		if (api > 0L || derived > 0L) {
			List<PvTooltip.Line> tip = new ArrayList<>();
			tip.add(PvTooltip.Line.title("Nucleus runs", PvDraw.COLOR_TEXT));
			tip.add(PvTooltip.Line.divider());
			tip.add(PvTooltip.Line.row("From crystals", PvDraw.COLOR_MUTED, FormatUtil.commas(derived), PvDraw.COLOR_ACCENT));
			if (api > 0L) {
				tip.add(PvTooltip.Line.row("API", PvDraw.COLOR_MUTED, FormatUtil.commas(api), PvDraw.COLOR_GOLD));
			}
			zones.add(HoverZone.of(rx, nucY, rw, MiningUi.STAT_ROW, tip));
		}

		if (hollows) {
			int hy = nucY + MiningUi.STAT_ROW + 6;
			if (snapshot.goblinKingQuests() > 0) {
				hy = MiningUi.statLine(g, font, "Goblin King quests",
					String.valueOf(snapshot.goblinKingQuests()), rx, hy, rw, PvDraw.COLOR_TEXT) + 1;
			}
			hy = MiningUi.statLine(g, font, "Jungle Temple",
				snapshot.jungleTempleOpen() ? "Open" : "Closed",
				rx, hy, rw, snapshot.jungleTempleOpen() ? MiningUi.PLACED : PvDraw.COLOR_MUTED) + 1;
			MiningUi.statLine(g, font, "Precursor",
				snapshot.precursorTalked() ? "Yes" : "No",
				rx, hy, rw, snapshot.precursorTalked() ? MiningUi.PLACED : PvDraw.COLOR_MUTED);
		}
	}

	private void drawCrystalCell(
		GuiGraphicsExtractor g, Font font, MiningSnapshot.Crystal c, int x, int y, int w, int cellH
	) {
		boolean collected = c.found();
		int nameColor = collected ? crystalColor(c.id()) : mixMuted(crystalColor(c.id()));
		int stateColor = c.placed() ? MiningUi.PLACED : (c.found() ? MiningUi.FOUND : MiningUi.MISSING);
		String state = prettyState(c.state());

		int iconY = y + Math.max(0, (cellH - CRYSTAL_ICON) / 2);
		drawCrystalIcon(g, c.id(), x, iconY, CRYSTAL_ICON);

		int textX = x + CRYSTAL_ICON + 4;
		int textW = Math.max(8, w - CRYSTAL_ICON - 4);
		int textY = y + Math.max(0, (cellH - font.lineHeight) / 2);
		String name = MiningUi.trim(font, c.name(), Math.max(8, textW - font.width(state) - 6));
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

	private static String prettyState(String state) {
		if (state == null || state.isBlank()) {
			return "-";
		}
		return switch (state.toUpperCase(Locale.ROOT)) {
			case "PLACED" -> "Placed";
			case "FOUND" -> "Found";
			case "NOT_FOUND" -> "Missing";
			default -> MiningUi.title(state);
		};
	}
}
