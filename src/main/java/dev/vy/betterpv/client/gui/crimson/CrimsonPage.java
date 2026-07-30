package dev.vy.betterpv.client.gui.crimson;

import dev.vy.betterpv.client.data.AbiphoneNpcs;
import dev.vy.betterpv.client.data.CrimsonKuudraCard;
import dev.vy.betterpv.client.data.CrimsonSnapshot;
import dev.vy.betterpv.client.data.DungeonSnapshot;
import dev.vy.betterpv.client.data.FormatUtil;
import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.PvTooltip;
import dev.vy.betterpv.client.gui.inventories.SkyBlockItemFactory;
import dev.vy.betterpv.client.gui.nav.PvSubTab;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Crimson: Overview (Dojo on flip) / Kuudra / Abiphone. */
public final class CrimsonPage {
	private static final int PAD = 6;
	private static final int GAP = 6;
	private static final int STAT_ROW = 12;
	private static final int SLOT = 18;
	private static final int SLOT_GAP = 3;
	private static final int ITEM_SLOT_BG = 0xFF101018;
	private static final int ITEM_SLOT_BORDER = 0xFF2A2A35;
	private static final int FLIP_MS = 480;
	private static final int SEP_GAP = 10;
	private static final int PANEL_HOVER = 0x0AFFFFFF;
	/** Mages - light purple. */
	private static final int MAGE_COLOR = 0xFFD97FFF;
	/** Barbarians - light red. */
	private static final int BARB_COLOR = 0xFFFF7777;
	/** Kuudra - red. */
	private static final int KUUDRA_COLOR = 0xFFFF5555;
	/** Dojo - gold. */
	private static final int DOJO_COLOR = 0xFFFFAA00;
	/** Crimson essence shop header - red. */
	private static final int SHOP_HEADER_COLOR = 0xFFFF5555;
	private static final int ENABLED = 0xFF55FF55;
	private static final int DISABLED = 0xFF555555;

	private CrimsonSnapshot snapshot = CrimsonSnapshot.empty();
	private final List<HoverZone> zones = new ArrayList<>();

	/** false = Dojo face shown (default), true = Crimson Essence Shop face shown. */
	private boolean showingShop;
	private boolean flipTarget;
	private long flipStartMs;
	private int flipHitX;
	private int flipHitY;
	private int flipHitW;
	private int flipHitH;

	private int abiphoneScroll;
	private int abiphoneMaxScroll;
	private int abiphoneX;
	private int abiphoneY;
	private int abiphoneW;
	private int abiphoneH;

	private int kuudraScroll;
	private int kuudraMaxScroll;
	private int kuudraX;
	private int kuudraY;
	private int kuudraW;
	private int kuudraH;

	public void apply(CrimsonSnapshot snapshot) {
		this.snapshot = snapshot == null ? CrimsonSnapshot.empty() : snapshot;
		this.abiphoneScroll = 0;
		this.kuudraScroll = 0;
	}

	public CrimsonSnapshot snapshot() {
		return this.snapshot;
	}

	public boolean mouseClicked(double mx, double my) {
		if (mx >= this.flipHitX && mx < this.flipHitX + this.flipHitW
			&& my >= this.flipHitY && my < this.flipHitY + this.flipHitH) {
			if (this.flipStartMs == 0L) {
				this.flipTarget = !this.showingShop;
				this.flipStartMs = System.currentTimeMillis();
			}
			return true;
		}
		return false;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY, PvSubTab sub) {
		if (sub == PvSubTab.CRIMSON_ABIPHONE && this.abiphoneMaxScroll > 0
			&& mouseX >= this.abiphoneX && mouseX < this.abiphoneX + this.abiphoneW
			&& mouseY >= this.abiphoneY && mouseY < this.abiphoneY + this.abiphoneH) {
			int delta = scrollY > 0 ? -14 : 14;
			int next = Math.max(0, Math.min(this.abiphoneMaxScroll, this.abiphoneScroll + delta));
			if (next != this.abiphoneScroll) {
				this.abiphoneScroll = next;
				return true;
			}
			return false;
		}
		if (sub == PvSubTab.CRIMSON_KUUDRA && this.kuudraMaxScroll > 0
			&& mouseX >= this.kuudraX && mouseX < this.kuudraX + this.kuudraW
			&& mouseY >= this.kuudraY && mouseY < this.kuudraY + this.kuudraH) {
			int delta = scrollY > 0 ? -14 : 14;
			int next = Math.max(0, Math.min(this.kuudraMaxScroll, this.kuudraScroll + delta));
			if (next != this.kuudraScroll) {
				this.kuudraScroll = next;
				return true;
			}
		}
		return false;
	}

	public void render(
		GuiGraphicsExtractor g, Font font, PvSubTab sub,
		int x, int y, int w, int h, int mouseX, int mouseY, int screenW, int screenH
	) {
		this.zones.clear();
		this.flipHitW = 0;
		this.flipHitH = 0;
		if (sub == PvSubTab.CRIMSON_KUUDRA) {
			renderKuudra(g, font, x, y, w, h, mouseX, mouseY);
		} else if (sub == PvSubTab.CRIMSON_ABIPHONE) {
			renderAbiphone(g, font, x, y, w, h, mouseX, mouseY);
		} else {
			renderOverview(g, font, x, y, w, h, mouseX, mouseY);
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

	private void drawOverviewLeft(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my
	) {
		int lx = x + PAD;
		int ly = y + PAD;
		int lw = w - PAD * 2;

		ly = statLine(g, font, "Selected faction", this.snapshot.factionLabel(),
			lx, ly, lw, this.snapshot.factionColor(MAGE_COLOR, BARB_COLOR, PvDraw.COLOR_MUTED)) + 2;

		ly = drawFactionCards(g, font, lx, ly, lw) + 4;

		ly = sectionSeparator(g, font, x, ly, w);
		ly = statLine(g, font, "Crimson essence", FormatUtil.commas(this.snapshot.crimsonEssence()),
			lx, ly, lw, PvDraw.COLOR_GOLD);
		ly = statLine(g, font, "Matriarch pearls", FormatUtil.commas(this.snapshot.matriarchPearls()),
			lx, ly, lw, PvDraw.COLOR_ACCENT);
		if (this.snapshot.matriarchLastAttemptMs() > 0L) {
			ly = statLine(g, font, "Last pearl attempt", formatAgo(this.snapshot.matriarchLastAttemptMs()),
				lx, ly, lw, PvDraw.COLOR_MUTED);
		}

		ly = sectionSeparator(g, font, x, ly, w);
		ly = statLine(g, font, "Kuudra clears", FormatUtil.commas(this.snapshot.kuudraTotalCompletions()),
			lx, ly, lw, KUUDRA_COLOR);
		int wave = this.snapshot.kuudraHighestClearedWave();
		statLine(g, font, "Highest wave", wave > 0 ? String.valueOf(wave) : "-",
			lx, ly, lw, PvDraw.COLOR_TEXT);
	}

	/** Side-by-side bordered faction cards. */
	private int drawFactionCards(GuiGraphicsExtractor g, Font font, int x, int y, int w) {
		int gap = 8;
		int colW = (w - gap) / 2;
		int cardPad = 4;
		int iconSize = 16;
		int cardH = cardPad * 2 + iconSize + 2 + STAT_ROW;

		drawFactionCard(g, font, "MAGE", new ItemStack(Items.BLAZE_POWDER),
			this.snapshot.magesReputation(), MAGE_COLOR, x, y, colW, cardH, cardPad, iconSize);
		drawFactionCard(g, font, "BARBARIAN", new ItemStack(Items.IRON_AXE),
			this.snapshot.barbariansReputation(), BARB_COLOR, x + colW + gap, y, colW, cardH, cardPad, iconSize);
		return y + cardH;
	}

	private void drawFactionCard(
		GuiGraphicsExtractor g, Font font, String name, ItemStack icon,
		double reputation, int color, int x, int y, int w, int h, int pad, int iconSize
	) {
		PvDraw.fill(g, x, y, w, h, ITEM_SLOT_BG);
		g.outline(x, y, w, h, PvDraw.COLOR_BORDER);

		int ix = x + pad;
		int iy = y + pad;
		drawItemIcon(g, icon, ix, iy, iconSize);
		int textY = iy + Math.max(0, (iconSize - font.lineHeight) / 2);
		PvDraw.text(g, font, name, ix + iconSize + 3, textY, color);

		int statY = iy + iconSize + 2;
		PvDraw.text(g, font, "Reputation:", ix, statY, PvDraw.COLOR_MUTED);
		PvDraw.textRight(g, font, FormatUtil.commas(Math.round(reputation)), x + w - pad, statY, color);
	}

	private void drawRightFlipPanel(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my
	) {
		this.flipHitX = x;
		this.flipHitY = y;
		this.flipHitW = w;
		this.flipHitH = h;

		boolean hovered = mx >= x && mx < x + w && my >= y && my < y + h;
		float flipProgress = 0F;
		boolean animating = this.flipStartMs != 0L;
		if (animating) {
			flipProgress = Math.min(1F, (System.currentTimeMillis() - this.flipStartMs) / (float) FLIP_MS);
			if (flipProgress >= 1F) {
				this.showingShop = this.flipTarget;
				this.flipStartMs = 0L;
				animating = false;
				flipProgress = 0F;
			}
		}
		float eased = animating ? easeInOutCubic(flipProgress) : 0F;
		float angle = eased * (float) Math.PI;
		boolean showShop = animating
			? (Math.cos(angle) < 0.0 ? this.flipTarget : this.showingShop)
			: this.showingShop;
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

		if (showShop) {
			drawShopFace(g, font, x, y, w, h);
		} else {
			drawDojoFace(g, font, x, y, w, h);
		}

		g.pose().popMatrix();
	}

	private void drawDojoFace(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h) {
		int lx = x + PAD;
		int ly = y + PAD;
		int lw = w - PAD * 2;

		PvDraw.text(g, font, "Dojo", lx, ly, PvDraw.COLOR_MUTED);
		PvDraw.textRight(g, font, "Total Points: " + FormatUtil.commas(this.snapshot.dojoTotalPoints()),
			lx + lw, ly, DOJO_COLOR);
		ly += font.lineHeight + 2;

		int colGap = 6;
		int colW = (lw - colGap) / 2;
		int leftCol = lx;
		int rightCol = lx + colW + colGap;
		CrimsonSnapshot.DojoChallenge[] challenges = CrimsonSnapshot.DojoChallenge.values();
		int rows = (challenges.length + 1) / 2;
		int bottom = y + h - PAD;
		int available = Math.max(rows * 22, bottom - ly);
		int entryGap = 1;
		// Icon + Points + Rank (no time).
		int entryH = Math.max(16 + STAT_ROW * 2, (available - (rows - 1) * entryGap) / rows);
		entryH = Math.min(entryH, 16 + STAT_ROW * 3);

		for (int i = 0; i < challenges.length; i++) {
			int col = i % 2;
			int row = i / 2;
			int cx = col == 0 ? leftCol : rightCol;
			int cy = ly + row * (entryH + entryGap);
			if (cy + 16 + STAT_ROW > bottom) {
				break;
			}
			drawDojoEntry(g, font, challenges[i], cx, cy, colW, entryH);
		}
	}

	private void drawDojoEntry(
		GuiGraphicsExtractor g, Font font, CrimsonSnapshot.DojoChallenge challenge,
		int x, int y, int w, int entryH
	) {
		CrimsonSnapshot.DojoScore score = this.snapshot.dojo(challenge);
		int icon = 16;
		drawItemIcon(g, dojoIcon(challenge), x, y, icon);
		int textY = y + Math.max(0, (icon - font.lineHeight) / 2);
		PvDraw.text(g, font, challenge.label(), x + icon + 3, textY, PvDraw.COLOR_TEXT);

		int ly = y + icon + 1;
		ly = statLine(g, font, "Points", FormatUtil.commas(score.points()), x, ly, w, DOJO_COLOR);
		if (ly + font.lineHeight <= y + entryH) {
			String rank = CrimsonSnapshot.dojoRank(score.points());
			statLine(g, font, "Rank", rank, x, ly, w, CrimsonSnapshot.dojoRankColor(rank));
		}
	}

	private static ItemStack dojoIcon(CrimsonSnapshot.DojoChallenge challenge) {
		return switch (challenge) {
			case MOB_KB -> new ItemStack(Items.BLAZE_ROD);
			case WALL_JUMP -> new ItemStack(Items.RABBIT_FOOT);
			case ARCHER -> new ItemStack(Items.LEAD);
			case SWORD_SWAP -> new ItemStack(Items.DIAMOND_SWORD);
			case SNAKE -> new ItemStack(Items.BOW);
			case FIREBALL -> new ItemStack(Items.FIRE_CHARGE);
			case LOCK_HEAD -> new ItemStack(Items.ENDER_EYE);
		};
	}

	private void drawShopFace(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h) {
		int lx = x + PAD;
		int ly = y + PAD;
		int lw = w - PAD * 2;
		int bottom = y + h - PAD;

		DungeonSnapshot.EssenceShop shop = this.snapshot.crimsonShop();
		int headerIcon = 16;
		int headerH = Math.max(headerIcon, font.lineHeight + 2);
		int headerIconMid = Math.max(0, (headerH - headerIcon) / 2);
		int headerTextMid = Math.max(0, (headerH - font.lineHeight) / 2);

		drawItemIcon(g, essenceIcon(shop.iconId()), lx, ly + headerIconMid, headerIcon);
		int labelX = lx + headerIcon + 4;
		String bal = FormatUtil.commas(shop.balance());
		int balW = PvDraw.widthBold(font, bal);
		int nameMax = Math.max(8, lw - (labelX - lx) - balW - 4);
		PvDraw.textBold(g, font, trim(font, "Crimson", nameMax), labelX, ly + headerTextMid, SHOP_HEADER_COLOR);
		PvDraw.textBold(g, font, bal, lx + lw - balW, ly + headerTextMid, SHOP_HEADER_COLOR);

		int perkIconSize = 12;
		int rowH = Math.max(font.lineHeight + 2, perkIconSize + 2);
		int colGap = 10;
		int colW = Math.max(40, (lw - colGap) / 2);
		int leftCol = lx;
		int rightCol = lx + colW + colGap;
		List<DungeonSnapshot.EssencePerk> perks = shop.perks();
		int mid = (perks.size() + 1) / 2;

		drawShopPerkColumn(g, font, perks.subList(0, Math.min(mid, perks.size())),
			leftCol, ly + headerH + 4, colW, rowH, perkIconSize, bottom);
		if (mid < perks.size()) {
			drawShopPerkColumn(g, font, perks.subList(mid, perks.size()),
				rightCol, ly + headerH + 4, colW, rowH, perkIconSize, bottom);
		}
	}

	private void drawShopPerkColumn(
		GuiGraphicsExtractor g, Font font, List<DungeonSnapshot.EssencePerk> perks,
		int x, int y, int w, int rowH, int perkIconSize, int bottom
	) {
		int perkLabelX = x + perkIconSize + 3;
		int perkIconMid = Math.max(0, (rowH - perkIconSize) / 2);
		int perkTextMid = Math.max(0, (rowH - font.lineHeight) / 2);
		int ry = y;
		for (DungeonSnapshot.EssencePerk perk : perks) {
			if (ry + font.lineHeight > bottom) {
				break;
			}
			drawItemIcon(g, shopPerkIcon(perk.id()), x, ry + perkIconMid, perkIconSize);
			String right = perk.level() + "/" + perk.maxLevel();
			int rightW = font.width(right);
			String left = trim(font, perk.name(), Math.max(8, w - (perkLabelX - x) - rightW - 4));
			int valueColor = perk.maxed() ? ENABLED : PvDraw.COLOR_TEXT;
			PvDraw.text(g, font, left, perkLabelX, ry + perkTextMid, PvDraw.COLOR_MUTED);
			PvDraw.textRight(g, font, right, x + w, ry + perkTextMid, valueColor);
			ry += rowH;
		}
	}

	private static ItemStack essenceIcon(String skyblockId) {
		String id = skyblockId == null || skyblockId.isBlank() ? "ESSENCE_CRIMSON" : skyblockId;
		ItemStack stack = SkyBlockItemFactory.iconStack(id);
		return stack.isEmpty() ? new ItemStack(Items.NETHER_STAR) : stack;
	}

	/** Crimson essence shop row icons (vanilla stand-ins matching the GUI). */
	private static ItemStack shopPerkIcon(String perkId) {
		if (perkId == null) {
			return new ItemStack(Items.PAPER);
		}
		return switch (perkId) {
			case "strongarm_kuudra" -> new ItemStack(Items.FISHING_ROD);
			case "fresh_tools_kuudra" -> new ItemStack(Items.IRON_PICKAXE);
			case "headstart_kuudra" -> new ItemStack(Items.GOLD_NUGGET);
			case "master_kuudra" -> new ItemStack(Items.NETHERITE_SWORD);
			case "fungus_fortuna" -> new ItemStack(Items.RED_MUSHROOM);
			case "harena_fortuna" -> new ItemStack(Items.RED_SAND);
			case "crimson_training" -> new ItemStack(Items.BOOK);
			case "wither_piper" -> new ItemStack(Items.WITHER_SKELETON_SKULL);
			default -> new ItemStack(Items.PAPER);
		};
	}

	private void renderKuudra(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my
	) {
		int leftW = Math.max(140, w * 40 / 100);
		int rightW = w - leftW - GAP;
		PvDraw.innerPanel(g, x, y, leftW, h);
		PvDraw.innerPanel(g, x + leftW + GAP, y, rightW, h);
		drawKuudraTiers(g, font, x, y, leftW, h);
		drawKuudraCard(g, font, x + leftW + GAP, y, rightW, h, mx, my);
	}

	private void drawKuudraTiers(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h) {
		int lx = x + PAD;
		int ly = y + PAD;
		int lw = w - PAD * 2;

		PvDraw.text(g, font, "Kuudra", lx, ly, PvDraw.COLOR_MUTED);
		ly += font.lineHeight + 4;
		ly = sectionSeparator(g, font, x, ly, w);

		for (CrimsonSnapshot.KuudraTier tier : CrimsonSnapshot.KuudraTier.values()) {
			if (ly + STAT_ROW * 2 + 6 > y + h - PAD) {
				break;
			}
			CrimsonSnapshot.KuudraTierStats stats = this.snapshot.kuudra(tier);
			PvDraw.text(g, font, tier.label() + ":", lx, ly, PvDraw.COLOR_MUTED);
			PvDraw.textRight(g, font, FormatUtil.commas(stats.completions()),
				lx + lw, ly, KUUDRA_COLOR);
			ly += STAT_ROW;
			PvDraw.text(g, font, "Highest wave:", lx, ly, PvDraw.COLOR_MUTED);
			PvDraw.textRight(g, font,
				stats.highestWave() > 0 ? String.valueOf(stats.highestWave()) : "-",
				lx + lw, ly, PvDraw.COLOR_TEXT);
			ly += STAT_ROW + 6;
		}
	}

	private void drawKuudraCard(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my
	) {
		CrimsonKuudraCard card = this.snapshot.kuudraCard();
		int lx = x + PAD;
		int contentTop = y + PAD;
		int lw = w - PAD * 2;
		int bottom = y + h - PAD;

		int contentH = measureKuudraCard(font, card);
		this.kuudraX = x;
		this.kuudraY = contentTop;
		this.kuudraW = w;
		this.kuudraH = Math.max(0, bottom - contentTop);
		this.kuudraMaxScroll = Math.max(0, contentH - this.kuudraH);
		this.kuudraScroll = Math.min(this.kuudraScroll, this.kuudraMaxScroll);

		g.enableScissor(lx, this.kuudraY, lx + lw, this.kuudraY + this.kuudraH);
		int ly = contentTop - this.kuudraScroll;
		drawKuudraCardBody(g, font, card, lx, ly, lw, mx, my, this.kuudraY, this.kuudraY + this.kuudraH);
		g.disableScissor();
	}

	private int measureKuudraCard(Font font, CrimsonKuudraCard card) {
		int ly = 0;
		ly += font.lineHeight + 2;
		ly += STAT_ROW;
		ly += STAT_ROW;
		ly += STAT_ROW;
		ly += SEP_GAP;
		ly += font.lineHeight + 3;
		ly += card.importantItems().size() * STAT_ROW;
		ly += SEP_GAP;
		ly += font.lineHeight + 3;
		ly += SLOT + 4;
		return ly;
	}

	private void drawKuudraCardBody(
		GuiGraphicsExtractor g, Font font, CrimsonKuudraCard card,
		int lx, int ly, int lw, int mx, int my, int clipTop, int clipBottom
	) {
		String scoreText = FormatUtil.shortXp(card.kuudraScore())
			+ " [Level: " + FormatUtil.commas(card.kuudraLevel()) + "]";
		PvDraw.text(g, font, "Kuudra Score:", lx, ly, PvDraw.COLOR_MUTED);
		PvDraw.textRight(g, font, scoreText, lx + lw, ly, KUUDRA_COLOR);
		if (visible(ly, font.lineHeight, clipTop, clipBottom)) {
			List<PvTooltip.Line> tip = new ArrayList<>();
			for (String line : card.scoreHoverLines()) {
				tip.add(PvTooltip.Line.of(line, PvDraw.COLOR_MUTED));
			}
			this.zones.add(HoverZone.of(lx, Math.max(ly, clipTop), lw,
				Math.min(ly + font.lineHeight, clipBottom) - Math.max(ly, clipTop), tip));
		}
		ly += font.lineHeight + 2;

		String mp = FormatUtil.commas(card.magicalPower())
			+ " [" + card.selectedPowerLabel() + "]";
		ly = statLine(g, font, "Magical Power", trim(font, mp, lw - font.width("Magical Power") - 8),
			lx, ly, lw, PvDraw.COLOR_ACCENT);

		String skills = "Cata " + card.cataLevel()
			+ ", Combat " + card.combatLevel()
			+ ", Foraging " + card.foragingLevel()
			+ ", SB " + card.skyBlockLevel();
		ly = statLine(g, font, "Skills", trim(font, skills, lw - font.width("Skills") - 8),
			lx, ly, lw, PvDraw.COLOR_TEXT);

		String vanq = String.format(Locale.ROOT, "%.3f%%", card.vanquisherChancePct());
		int vanqY = ly;
		ly = statLine(g, font, "Vanquisher Chance", vanq, lx, ly, lw, ENABLED);
		if (visible(vanqY, STAT_ROW, clipTop, clipBottom)) {
			List<PvTooltip.Line> tip = new ArrayList<>();
			tip.add(PvTooltip.Line.of("Vanquisher Chance", PvDraw.COLOR_TEXT));
			for (String line : card.vanquisherHover()) {
				tip.add(PvTooltip.Line.of(line, PvDraw.COLOR_MUTED));
			}
			this.zones.add(HoverZone.of(lx, Math.max(vanqY, clipTop), lw,
				Math.min(vanqY + STAT_ROW, clipBottom) - Math.max(vanqY, clipTop), tip));
		}

		ly = sectionSeparator(g, font, lx - PAD, ly, lw + PAD * 2);
		PvDraw.text(g, font, "Important Items", lx, ly, PvDraw.COLOR_MUTED);
		ly += font.lineHeight + 3;

		for (CrimsonKuudraCard.ImportantItem item : card.importantItems()) {
			if (visible(ly, STAT_ROW, clipTop, clipBottom)) {
				int color = item.owned() ? ENABLED : DISABLED;
				String mark = item.owned() ? "✔ " : "✘ ";
				String detail = item.details().isEmpty() ? "" : " [" + String.join(" · ", item.details()) + "]";
				String line = mark + item.label() + detail;
				PvDraw.text(g, font, trim(font, line, lw), lx, ly, color);
				addLoreHoverZone(lx, Math.max(ly, clipTop), lw,
					Math.min(ly + STAT_ROW, clipBottom) - Math.max(ly, clipTop),
					item.label(), item.displayName(), item.owned(), item.details(), item.lore());
			}
			ly += STAT_ROW;
		}

		ly = sectionSeparator(g, font, lx - PAD, ly, lw + PAD * 2);
		int colGap = 10;
		int colW = Math.max(SLOT, (lw - colGap) / 2);
		int rightColX = lx + colW + colGap;
		PvDraw.text(g, font, "Mage Armor", lx, ly, PvDraw.COLOR_MUTED);
		PvDraw.text(g, font, "Archer Armor", rightColX, ly, PvDraw.COLOR_MUTED);
		ly += font.lineHeight + 3;
		drawArmorRow(g, font, card.mageArmor(), lx, ly, mx, my, clipTop, clipBottom);
		drawArmorRow(g, font, card.archerArmor(), rightColX, ly, mx, my, clipTop, clipBottom);
	}

	private int drawArmorRow(
		GuiGraphicsExtractor g, Font font, List<CrimsonKuudraCard.ArmorPiece> pieces,
		int lx, int ly, int mx, int my, int clipTop, int clipBottom
	) {
		int i = 0;
		for (CrimsonKuudraCard.ArmorPiece piece : pieces) {
			int bx = lx + i * (SLOT + SLOT_GAP);
			int by = ly;
			boolean hovered = mx >= bx && mx < bx + SLOT && my >= by && my < by + SLOT
				&& my >= clipTop && my < clipBottom;
			if (visible(by, SLOT, clipTop, clipBottom)) {
				PvDraw.fill(g, bx, by, SLOT, SLOT, ITEM_SLOT_BG);
				g.outline(bx, by, SLOT, SLOT,
					hovered ? PvDraw.COLOR_ACCENT : (piece.owned() ? ITEM_SLOT_BORDER : DISABLED));
				ItemStack icon = armorIconStack(piece);
				int iconPad = Math.max(0, (SLOT - 16) / 2);
				if (!icon.isEmpty()) {
					g.item(icon, bx + iconPad, by + iconPad);
					if (!piece.owned()) {
						PvDraw.fill(g, bx + 1, by + 1, SLOT - 2, SLOT - 2, 0x88000000);
					}
				} else {
					g.item(new ItemStack(Items.GRAY_DYE), bx + iconPad, by + iconPad);
				}
				if (piece.owned() && piece.dyeColor() != null) {
					PvDraw.fill(g, bx + SLOT - 4, by + 1, 3, 3, 0xFF000000 | (piece.dyeColor() & 0xFFFFFF));
				}
				int y0 = Math.max(by, clipTop);
				int y1 = Math.min(by + SLOT, clipBottom);
				if (y1 > y0) {
					addLoreHoverZone(bx, y0, SLOT, y1 - y0,
						piece.label().isBlank() ? piece.slot() : piece.label(),
						piece.displayName(),
						piece.owned(), piece.details(), piece.lore());
				}
			}
			i++;
		}
		return ly + SLOT + 4;
	}

	private static ItemStack armorIconStack(CrimsonKuudraCard.ArmorPiece piece) {
		ItemStack icon = SkyBlockItemFactory.iconStack(piece.iconId());
		if (icon.isEmpty()) {
			return icon;
		}
		ItemStack copy = icon.copy();
		if (piece.dyeColor() != null) {
			var item = copy.getItem();
			if (item == Items.LEATHER_HELMET || item == Items.LEATHER_CHESTPLATE
				|| item == Items.LEATHER_LEGGINGS || item == Items.LEATHER_BOOTS
				|| item == Items.LEATHER_HORSE_ARMOR) {
				copy.set(DataComponents.DYED_COLOR,
					new net.minecraft.world.item.component.DyedItemColor(piece.dyeColor() & 0xFFFFFF));
			}
		}
		return copy;
	}

	/** Full item lore (when present) via legacy-formatted components, else a plain fallback tip. */
	private void addLoreHoverZone(
		int x, int y, int w, int h,
		String label, String displayName, boolean owned, List<String> details, List<String> lore
	) {
		if (lore != null && !lore.isEmpty()) {
			List<Component> tip = new ArrayList<>();
			String title = displayName != null && !displayName.isBlank() ? displayName : label;
			tip.add(SkyBlockItemFactory.legacyLine(title == null ? "" : title));
			String titlePlain = stripFormatting(title);
			int start = 0;
			if (!lore.isEmpty()) {
				String firstPlain = stripFormatting(lore.get(0));
				if (!titlePlain.isBlank() && !firstPlain.isBlank()
					&& (firstPlain.equalsIgnoreCase(titlePlain)
					|| titlePlain.regionMatches(true, 0, firstPlain, 0, Math.min(titlePlain.length(), firstPlain.length()))
					|| firstPlain.regionMatches(true, 0, titlePlain, 0, Math.min(titlePlain.length(), firstPlain.length())))) {
					start = 1;
				}
			}
			for (int i = start; i < lore.size(); i++) {
				String loreLine = lore.get(i);
				tip.add(loreLine == null || loreLine.isBlank()
					? Component.empty()
					: SkyBlockItemFactory.legacyLine(loreLine));
			}
			this.zones.add(HoverZone.ofComponents(x, y, w, h, tip));
			return;
		}
		List<PvTooltip.Line> tip = new ArrayList<>();
		String title = displayName != null && !displayName.isBlank() ? stripFormatting(displayName) : label;
		tip.add(PvTooltip.Line.of(title == null ? "" : title, PvDraw.COLOR_TEXT));
		tip.add(PvTooltip.Line.of(owned ? "Owned" : "Missing", owned ? ENABLED : DISABLED));
		for (String d : details) {
			tip.add(PvTooltip.Line.of(d, PvDraw.COLOR_MUTED));
		}
		this.zones.add(HoverZone.of(x, y, w, h, tip));
	}

	private static String stripFormatting(String text) {
		if (text == null || text.isEmpty()) {
			return "";
		}
		return text.replaceAll("§.", "").replaceAll("&[0-9a-fk-or]", "");
	}

	private static boolean visible(int y, int h, int clipTop, int clipBottom) {
		return y + h > clipTop && y < clipBottom;
	}

	private void renderAbiphone(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my
	) {
		int statsW = Math.max(150, Math.min(220, w * 32 / 100));
		int iconsW = w - statsW - GAP;
		PvDraw.innerPanel(g, x, y, iconsW, h);
		PvDraw.innerPanel(g, x + iconsW + GAP, y, statsW, h);

		drawAbiphoneStats(g, font, x + iconsW + GAP, y, statsW, h);
		drawAbiphoneIcons(g, font, x, y, iconsW, h, mx, my);
	}

	private void drawAbiphoneStats(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h) {
		int lx = x + PAD;
		int ly = y + PAD;
		int lw = w - PAD * 2;

		PvDraw.text(g, font, "Abiphone", lx, ly, PvDraw.COLOR_MUTED);
		PvDraw.textRight(g, font,
			this.snapshot.abiphoneActive() + " / " + this.snapshot.abiphoneContacts().size(),
			lx + lw, ly, PvDraw.COLOR_ACCENT);
		ly += font.lineHeight + 4;
		ly = sectionSeparator(g, font, x, ly, w);

		if (!this.snapshot.abiphoneRingtone().isBlank()) {
			ly = statLine(g, font, "Ringtone", prettyRingtone(this.snapshot.abiphoneRingtone()),
				lx, ly, lw, PvDraw.COLOR_TEXT);
		}
		if (this.snapshot.trioContactAddons() > 0) {
			ly = statLine(g, font, "Trio addons", String.valueOf(this.snapshot.trioContactAddons()),
				lx, ly, lw, PvDraw.COLOR_GOLD);
		}
		if (this.snapshot.operatorChipRepaired() > 0) {
			ly = statLine(g, font, "Operator chip", "Repaired " + this.snapshot.operatorChipRepaired(),
				lx, ly, lw, PvDraw.COLOR_ACCENT);
		}
		ly = statLine(g, font, "Quests done",
			this.snapshot.abiphoneQuestsDone() + "/" + this.snapshot.abiphoneContacts().size(),
			lx, ly, lw, ENABLED);
		if (this.snapshot.abiphoneDndCount() > 0) {
			ly = statLine(g, font, "DND contacts", String.valueOf(this.snapshot.abiphoneDndCount()),
				lx, ly, lw, PvDraw.COLOR_MUTED);
		}
		if (this.snapshot.snakeBestScore() > 0 || this.snapshot.tttLosses() > 0 || this.snapshot.tttDraws() > 0) {
			String games = "Snake " + this.snapshot.snakeBestScore()
				+ " · TTT L" + this.snapshot.tttLosses()
				+ " D" + this.snapshot.tttDraws();
			ly = statLine(g, font, "Games", games, lx, ly, lw, DOJO_COLOR);
		}
		if (!this.snapshot.abiphoneSort().isBlank()) {
			ly = statLine(g, font, "Sort", prettyRingtone(this.snapshot.abiphoneSort()),
				lx, ly, lw, PvDraw.COLOR_MUTED);
		}
		ly = sectionSeparator(g, font, x, ly, w);
		PvDraw.text(g, font, "Last Called", lx, ly, PvDraw.COLOR_MUTED);
	}

	private void drawAbiphoneIcons(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my
	) {
		int lx = x + PAD;
		int ly = y + PAD;
		int lw = w - PAD * 2;
		int bottom = y + h - PAD;

		PvDraw.text(g, font, "Contacts", lx, ly, PvDraw.COLOR_MUTED);
		ly += font.lineHeight + 4;

		List<CrimsonSnapshot.AbiphoneContact> contacts = this.snapshot.abiphoneContacts();
		if (contacts.isEmpty()) {
			PvDraw.textCentered(g, font, "No contacts",
				x + w / 2, y + h / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED);
			this.abiphoneMaxScroll = 0;
			this.abiphoneW = 0;
			this.abiphoneH = 0;
			return;
		}

		int gridH = Math.max(1, bottom - ly);
		// Expand slot size to fill the left panel and reduce empty space.
		int maxCols = Math.max(5, Math.min(10, contacts.size()));
		int slot = 18;
		int gap = 3;
		for (int trySlot = 28; trySlot >= 18; trySlot--) {
			int tryGap = trySlot >= 24 ? 4 : 3;
			int cols = Math.max(1, (lw + tryGap) / (trySlot + tryGap));
			cols = Math.min(cols, maxCols);
			int rows = (contacts.size() + cols - 1) / cols;
			int needH = rows * (trySlot + tryGap) - tryGap;
			if (needH <= gridH || trySlot == 18) {
				slot = trySlot;
				gap = tryGap;
				break;
			}
		}
		int cols = Math.max(1, Math.min(maxCols, (lw + gap) / (slot + gap)));
		// Stretch gaps so the grid uses the full width.
		int usedW = cols * slot;
		int freeW = Math.max(0, lw - usedW);
		int gapX = cols > 1 ? freeW / (cols - 1) : 0;
		gapX = Math.max(gap, gapX);
		int gridRows = (contacts.size() + cols - 1) / cols;
		int contentH = gridRows * (slot + gap) - gap;
		this.abiphoneX = x;
		this.abiphoneY = ly;
		this.abiphoneW = w;
		this.abiphoneH = Math.max(0, bottom - ly);
		this.abiphoneMaxScroll = Math.max(0, contentH - this.abiphoneH);
		this.abiphoneScroll = Math.min(this.abiphoneScroll, this.abiphoneMaxScroll);

		g.enableScissor(lx, this.abiphoneY, lx + lw, this.abiphoneY + this.abiphoneH);
		for (int i = 0; i < contacts.size(); i++) {
			CrimsonSnapshot.AbiphoneContact contact = contacts.get(i);
			int col = i % cols;
			int row = i / cols;
			int bx = lx + col * (slot + gapX);
			int by = ly + row * (slot + gap) - this.abiphoneScroll;
			boolean hovered = mx >= bx && mx < bx + slot && my >= by && my < by + slot
				&& my >= this.abiphoneY && my < this.abiphoneY + this.abiphoneH;
			PvDraw.fill(g, bx, by, slot, slot, ITEM_SLOT_BG);
			g.outline(bx, by, slot, slot,
				hovered ? PvDraw.COLOR_ACCENT : (contact.active() ? ITEM_SLOT_BORDER : DISABLED));

			int iconPad = Math.max(0, (slot - 16) / 2);
			drawContactIcon(g, contact.id(), bx + iconPad, by + iconPad);
			if (!contact.active()) {
				PvDraw.fill(g, bx + 1, by + 1, slot - 2, slot - 2, 0x66000000);
			}

			int y0 = Math.max(by, this.abiphoneY);
			int y1 = Math.min(by + slot, this.abiphoneY + this.abiphoneH);
			if (y1 > y0) {
				List<PvTooltip.Line> tip = new ArrayList<>();
				tip.add(PvTooltip.Line.of(contact.name(), PvDraw.COLOR_TEXT));
				tip.add(PvTooltip.Line.of(contact.active() ? "Active contact" : "Inactive",
					contact.active() ? ENABLED : PvDraw.COLOR_MUTED));
				if (contact.completedQuest()) {
					tip.add(PvTooltip.Line.of("Quest complete", ENABLED));
				} else if (contact.talkedTo()) {
					tip.add(PvTooltip.Line.of("Talked to", PvDraw.COLOR_MUTED));
				}
				if (contact.incomingCalls() > 0) {
					tip.add(PvTooltip.Line.of("Incoming calls: " + FormatUtil.commas(contact.incomingCalls()),
						PvDraw.COLOR_MUTED));
				}
				if (contact.dnd()) {
					tip.add(PvTooltip.Line.of("Do Not Disturb", PvDraw.COLOR_GOLD));
				}
				this.zones.add(HoverZone.of(bx, y0, slot, y1 - y0, tip));
			}
		}
		g.disableScissor();
	}

	/** Prefer the custom NEU skin texture, then a vanilla stack; never show blank paper. */
	private static void drawContactIcon(GuiGraphicsExtractor g, String contactId, int x, int y) {
		String neuId = AbiphoneNpcs.neuId(contactId);
		if (!neuId.isBlank()) {
			Identifier custom = SkyBlockItemFactory.customIcon(neuId);
			if (custom != null) {
				int tex = SkyBlockItemFactory.customIconSize(neuId);
				// Draw native pixels into the 16×16 item box (no upscale blur).
				int draw = Math.min(16, Math.max(1, tex));
				int ox = (16 - draw) / 2;
				g.blit(RenderPipelines.GUI_TEXTURED, custom, x + ox, y + ox, 0, 0, draw, draw, tex, tex, tex, tex);
				return;
			}
		}
		ItemStack icon = neuId.isBlank() ? ItemStack.EMPTY : SkyBlockItemFactory.iconStack(neuId);
		if (icon.isEmpty() || icon.is(Items.PAPER) || isUntexturedHead(icon)) {
			icon = new ItemStack(Items.VILLAGER_SPAWN_EGG);
		}
		g.item(icon, x, y);
	}

	private static boolean isUntexturedHead(ItemStack icon) {
		if (icon == null || icon.isEmpty() || !icon.is(Items.PLAYER_HEAD)) {
			return false;
		}
		var profile = icon.get(DataComponents.PROFILE);
		if (profile == null) {
			return true;
		}
		try {
			var props = profile.partialProfile().properties().get("textures");
			if (props == null || props.isEmpty()) {
				return true;
			}
			for (var prop : props) {
				if (prop != null && prop.value() != null && !prop.value().isBlank()) {
					return false;
				}
			}
		} catch (Exception ignored) {
			return true;
		}
		return true;
	}

	/**
	 * Draw an item at native 16×16 when possible (avoids fuzzy downscales).
	 * Only scales when the target box is smaller than 16px.
	 */
	private static void drawItemIcon(GuiGraphicsExtractor g, ItemStack icon, int x, int y, int size) {
		if (icon == null || icon.isEmpty()) {
			return;
		}
		if (size >= 16) {
			int pad = (size - 16) / 2;
			g.item(icon, x + pad, y + pad);
			return;
		}
		float scale = size / 16F;
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.item(icon, 0, 0);
		g.pose().popMatrix();
	}

	private static int sectionSeparator(GuiGraphicsExtractor g, Font font, int panelX, int y, int panelW) {
		int lineInset = PAD + 4;
		int lineW = Math.max(0, panelW - lineInset * 2);
		int lineY = y + (SEP_GAP - 1) / 2;
		if (lineW > 0) {
			PvDraw.fill(g, panelX + lineInset, lineY, lineW, 1, 0x33FFFFFF);
		}
		return y + SEP_GAP;
	}

	private static int statLine(
		GuiGraphicsExtractor g, Font font, String label, String value, int x, int y, int w, int valueColor
	) {
		PvDraw.text(g, font, label, x, y, PvDraw.COLOR_MUTED);
		PvDraw.textRight(g, font, value == null || value.isBlank() ? "-" : value, x + w, y, valueColor);
		return y + STAT_ROW;
	}

	private void drawHover(GuiGraphicsExtractor g, Font font, int mx, int my, int screenW, int screenH) {
		for (HoverZone zone : this.zones) {
			if (mx >= zone.x && mx < zone.x + zone.w && my >= zone.y && my < zone.y + zone.h) {
				if (zone.components != null && !zone.components.isEmpty()) {
					PvTooltip.drawComponents(g, font, zone.components, mx, my, screenW, screenH);
				} else if (zone.lines != null) {
					PvTooltip.drawStyled(g, font, zone.lines, mx, my, screenW, screenH);
				}
				return;
			}
		}
	}

	private static float easeInOutCubic(float t) {
		return t < 0.5F ? 4F * t * t * t : 1F - (float) Math.pow(-2F * t + 2F, 3) / 2F;
	}

	private static String formatAgo(long ms) {
		if (ms <= 0L) {
			return "-";
		}
		long ago = Math.max(0L, System.currentTimeMillis() - ms);
		return FormatUtil.prettySpan(ago) + " ago";
	}

	private static String prettyRingtone(String raw) {
		if (raw == null || raw.isBlank()) {
			return "-";
		}
		String[] parts = raw.replace('-', '_').split("_");
		StringBuilder sb = new StringBuilder();
		for (String part : parts) {
			if (part.isBlank()) {
				continue;
			}
			if (!sb.isEmpty()) {
				sb.append(' ');
			}
			sb.append(Character.toUpperCase(part.charAt(0)));
			if (part.length() > 1) {
				sb.append(part.substring(1).toLowerCase(Locale.ROOT));
			}
		}
		return sb.toString();
	}

	private static String trim(Font font, String text, int maxW) {
		if (text == null) {
			return "";
		}
		if (font.width(text) <= maxW) {
			return text;
		}
		String ellipsis = "...";
		int budget = Math.max(0, maxW - font.width(ellipsis));
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (font.width(sb.toString() + c) > budget) {
				break;
			}
			sb.append(c);
		}
		return sb + ellipsis;
	}

	private record HoverZone(int x, int y, int w, int h, List<PvTooltip.Line> lines, List<Component> components) {
		static HoverZone of(int x, int y, int w, int h, List<PvTooltip.Line> lines) {
			return new HoverZone(x, y, w, h, lines, null);
		}

		static HoverZone ofComponents(int x, int y, int w, int h, List<Component> components) {
			return new HoverZone(x, y, w, h, null, components);
		}
	}
}
