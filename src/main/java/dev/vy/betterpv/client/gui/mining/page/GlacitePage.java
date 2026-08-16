package dev.vy.betterpv.client.gui.mining.page;

import dev.vy.betterpv.client.data.DungeonSnapshot;
import dev.vy.betterpv.client.data.FormatUtil;
import dev.vy.betterpv.client.data.MiningSnapshot;
import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.PvTooltip;
import dev.vy.betterpv.client.gui.inventories.SkyBlockItemFactory;
import dev.vy.betterpv.client.gui.mining.MiningUi;
import dev.vy.betterpv.client.gui.mining.MiningUi.HoverZone;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Glacite tunnels subtab: mineshafts, fossils, corpses, milestones. */
public final class GlacitePage {
	private static final int BAR_CORPSE = 0xFF6B9BD1;
	private static final int CORPSE_LAPIS = 0xFF5555FF;
	private static final int CORPSE_UMBER = 0xFFC4A35A;
	private static final int CORPSE_TUNGSTEN = 0xFFB0B0B8;
	private static final int CORPSE_VANGUARD = 0xFF55FFFF;

	private int scroll;
	private int maxScroll;
	private int scrollTop;
	private int scrollH;

	public void reset() {
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
		GuiGraphicsExtractor g, Font font, MiningSnapshot snapshot, List<HoverZone> zones,
		int x, int y, int w, int h, int mx, int my
	) {
		int rightW = Math.max(200, w * 55 / 100);
		int leftW = w - rightW - MiningUi.GAP;
		PvDraw.innerPanel(g, x, y, leftW, h);
		PvDraw.innerPanel(g, x + leftW + MiningUi.GAP, y, rightW, h);

		int lx = x + MiningUi.PAD;
		int ly = y + MiningUi.PAD;
		int lw = leftW - MiningUi.PAD * 2;

		PvDraw.text(g, font, "Glacite", lx, ly, PvDraw.COLOR_MUTED);
		ly += font.lineHeight + 4;

		ly = MiningUi.statLine(g, font, "Mineshafts entered", FormatUtil.commas(snapshot.mineshaftsEntered()),
			lx, ly, lw, PvDraw.COLOR_TEXT);
		ly = MiningUi.sectionSeparator(g, font, x, ly, leftW);
		ly = MiningUi.statLine(g, font, "Fossil dust", FormatUtil.commas(snapshot.fossilDust()),
			lx, ly, lw, MiningUi.BAR_GLACITE) + 1;

		List<String> fossils = snapshot.fossilsDonated();
		String fossilCount = String.valueOf(fossils.size());
		int fossilTop = ly;
		ly = MiningUi.statLine(g, font, "Fossils donated", fossilCount, lx, ly, lw, PvDraw.COLOR_ACCENT);
		if (!fossils.isEmpty()) {
			List<PvTooltip.Line> tip = new ArrayList<>();
			tip.add(PvTooltip.Line.of("Fossils donated", PvDraw.COLOR_MUTED));
			for (String fossil : fossils) {
				tip.add(PvTooltip.Line.of(MiningUi.title(fossil), MiningUi.PLACED));
			}
			zones.add(HoverZone.of(lx, fossilTop, lw, MiningUi.STAT_ROW, tip));
		}
		ly = MiningUi.sectionSeparator(g, font, x, ly, leftW);

		ly = drawCompactEssenceShop(g, font, snapshot.iceShop(), lx, ly, lw, y + h - MiningUi.PAD);

		MiningSnapshot.CorpseCounts counts = snapshot.corpses();
		PvDraw.text(g, font, "Corpses looted", lx, ly, PvDraw.COLOR_MUTED);
		ly += font.lineHeight + 3;
		ly = MiningUi.coloredLabelStat(g, font, "Lapis", FormatUtil.commas(counts.lapis()),
			lx, ly, lw, CORPSE_LAPIS, PvDraw.COLOR_TEXT) + 1;
		ly = MiningUi.coloredLabelStat(g, font, "Umber", FormatUtil.commas(counts.umber()),
			lx, ly, lw, CORPSE_UMBER, PvDraw.COLOR_TEXT) + 1;
		ly = MiningUi.coloredLabelStat(g, font, "Tungsten", FormatUtil.commas(counts.tungsten()),
			lx, ly, lw, CORPSE_TUNGSTEN, PvDraw.COLOR_TEXT) + 1;
		ly = MiningUi.coloredLabelStat(g, font, "Vanguard", FormatUtil.commas(counts.vanguard()),
			lx, ly, lw, CORPSE_VANGUARD, PvDraw.COLOR_TEXT) + 1;
		ly = MiningUi.statLine(g, font, "Total", FormatUtil.commas(counts.total()), lx, ly, lw, PvDraw.COLOR_ACCENT);
		ly = MiningUi.sectionSeparator(g, font, x, ly, leftW);

		int current = snapshot.corpseMilestone();
		String tierLabel = current <= 0 ? "None" : ("Tier " + current + (current >= 7 ? " (max)" : ""));
		MiningUi.statLine(g, font, "Milestone", tierLabel, lx, ly, lw, PvDraw.COLOR_ACCENT);

		renderCorpseMilestones(g, font, snapshot, zones, x + leftW + MiningUi.GAP, y, rightW, h, mx, my);
	}

	private void renderCorpseMilestones(
		GuiGraphicsExtractor g, Font font, MiningSnapshot snapshot, List<HoverZone> zones,
		int x, int y, int w, int h, int mx, int my
	) {
		int rx = x + MiningUi.PAD;
		int ry = y + MiningUi.PAD;
		int rw = w - MiningUi.PAD * 2;

		PvDraw.text(g, font, "Corpse milestones", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 4;

		MiningSnapshot.CorpseCounts counts = snapshot.corpses();
		int current = snapshot.corpseMilestone();

		int gridTop = ry;
		int gridH = y + h - MiningUi.PAD - gridTop;
		this.scrollTop = gridTop;
		this.scrollH = gridH;

		List<MiningSnapshot.CorpseMilestone> tiers = MiningSnapshot.CORPSE_MILESTONES;
		int rowH = Math.max(MiningUi.barRowH(font), gridH / Math.max(1, tiers.size()));
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
			yy = MiningUi.drawBar(g, font, "Tier " + tier.tier(), value, fill, done, BAR_CORPSE, hover,
				rx, yy, rw, mx, my, zones) + Math.max(MiningUi.BAR_AFTER, rowH - MiningUi.barRowH(font) + MiningUi.BAR_AFTER);
		}
		g.disableScissor();
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

	/** Compact ice essence shop: header bal + 2-col perk rows. */
	private static int drawCompactEssenceShop(
		GuiGraphicsExtractor g, Font font, DungeonSnapshot.EssenceShop shop,
		int x, int y, int w, int bottom
	) {
		if (shop == null || (shop.perks().isEmpty() && shop.balance() <= 0L)) {
			return y;
		}
		int headerIcon = 14;
		int headerH = Math.max(headerIcon, font.lineHeight + 2);
		int headerIconMid = Math.max(0, (headerH - headerIcon) / 2);
		int headerTextMid = Math.max(0, (headerH - font.lineHeight) / 2);
		MiningUi.drawItemIcon(g, iceIcon(shop.iconId()), x, y + headerIconMid, headerIcon);
		int labelX = x + headerIcon + 3;
		String bal = FormatUtil.commas(shop.balance());
		int balW = PvDraw.widthBold(font, bal);
		PvDraw.textBold(g, font, MiningUi.trim(font, shop.name(), Math.max(8, w - (labelX - x) - balW - 4)),
			labelX, y + headerTextMid, MiningUi.BAR_GLACITE);
		PvDraw.textBold(g, font, bal, x + w - balW, y + headerTextMid, MiningUi.BAR_GLACITE);

		int perkIcon = 10;
		int rowH = Math.max(font.lineHeight + 1, perkIcon + 2);
		int colGap = 8;
		int colW = Math.max(40, (w - colGap) / 2);
		List<DungeonSnapshot.EssencePerk> perks = shop.perks();
		int mid = (perks.size() + 1) / 2;
		int leftY = y + headerH + 3;
		int rightY = leftY;
		leftY = drawIcePerkColumn(g, font, perks.subList(0, Math.min(mid, perks.size())),
			x, leftY, colW, rowH, perkIcon, bottom);
		if (mid < perks.size()) {
			rightY = drawIcePerkColumn(g, font, perks.subList(mid, perks.size()),
				x + colW + colGap, rightY, colW, rowH, perkIcon, bottom);
		}
		return Math.max(leftY, rightY) + 4;
	}

	private static int drawIcePerkColumn(
		GuiGraphicsExtractor g, Font font, List<DungeonSnapshot.EssencePerk> perks,
		int x, int y, int w, int rowH, int perkIcon, int bottom
	) {
		int labelX = x + perkIcon + 2;
		int iconMid = Math.max(0, (rowH - perkIcon) / 2);
		int textMid = Math.max(0, (rowH - font.lineHeight) / 2);
		int ry = y;
		for (DungeonSnapshot.EssencePerk perk : perks) {
			if (ry + font.lineHeight > bottom) {
				break;
			}
			MiningUi.drawItemIcon(g, icePerkIcon(perk.id()), x, ry + iconMid, perkIcon);
			String right = perk.level() + "/" + perk.maxLevel();
			int rightW = font.width(right);
			PvDraw.text(g, font, MiningUi.trim(font, perk.name(), Math.max(8, w - (labelX - x) - rightW - 4)),
				labelX, ry + textMid, PvDraw.COLOR_MUTED);
			PvDraw.textRight(g, font, right, x + w, ry + textMid,
				perk.maxed() ? MiningUi.PLACED : PvDraw.COLOR_TEXT);
			ry += rowH;
		}
		return ry;
	}

	private static ItemStack iceIcon(String id) {
		ItemStack stack = SkyBlockItemFactory.iconStack(id == null ? "ESSENCE_ICE" : id);
		return stack == null || stack.isEmpty() ? new ItemStack(Items.BLUE_ICE) : stack;
	}

	private static ItemStack icePerkIcon(String perkId) {
		if (perkId == null) {
			return new ItemStack(Items.PAPER);
		}
		return switch (perkId) {
			case "cold_efficiency" -> new ItemStack(Items.IRON_PICKAXE);
			case "cooled_forges" -> new ItemStack(Items.BLAST_FURNACE);
			case "frozen_skin" -> new ItemStack(Items.LEATHER_CHESTPLATE);
			case "season_of_joy" -> new ItemStack(Items.SNOWBALL);
			case "drake_piper" -> new ItemStack(Items.EGG);
			default -> new ItemStack(Items.PAPER);
		};
	}
}
