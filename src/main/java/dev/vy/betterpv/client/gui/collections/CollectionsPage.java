package dev.vy.betterpv.client.gui.collections;

import com.mojang.authlib.GameProfile;
import com.google.gson.JsonObject;
import dev.vy.betterpv.client.api.HypixelApiClient;
import dev.vy.betterpv.client.cosmetics.PlayerCustomizationRegistry;
import dev.vy.betterpv.client.data.BossCollections;
import dev.vy.betterpv.client.data.CollectionIds;
import dev.vy.betterpv.client.data.CollectionSnapshot;
import dev.vy.betterpv.client.data.FormatUtil;
import dev.vy.betterpv.client.data.HypixelRanks;
import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.PvTooltip;
import dev.vy.betterpv.client.gui.inventories.SkyBlockItemFactory;
import dev.vy.betterpv.client.gui.nav.PvSubTab;
import dev.vy.betterpv.client.price.HypixelCollectionsCache;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Collections: full-width multi-column board; amount/tier/coop on hover.
 * Minions: icon grid + tier checklist.
 */
public final class CollectionsPage {
	private static final int GAP = 8;
	private static final int PAD = 6;
	private static final int SLOT = 20;
	private static final int SLOT_GAP = 3;
	private static final int ITEM_ICON = 16;
	/**
	 * Collections: pick the largest crisp icon that fits without scrolling.
	 * Discrete sizes only - fractional scales look muddy.
	 */
	private static final int[] COLL_ICON_STEPS = {16, 14, 12, 10};
	private static final int MINION_UNLOCKED = 0xFF98E898;
	private static final int MINION_LOCKED = 0xFFFF9999;
	/** Hypixel SkyBlock heavy marks (not the thin ✓/✗). */
	private static final String MINION_TICK = "\u2714";
	private static final String MINION_CROSS = "\u2716";

	private CollectionSnapshot snapshot = CollectionSnapshot.empty();
	private int selectedCat;
	private int selectedItem;
	private int selectedMinion;
	private int gridScroll;
	private int gridMaxScroll;
	private int minionScroll;
	private int minionMaxScroll;
	private int tierScroll;
	private int tierMaxScroll;
	private int gridTop;
	private int gridH;
	private int minionTop;
	private int minionH;
	private int tierListTop;
	private int tierListH;
	private final List<ItemHit> itemHits = new ArrayList<>();
	private final List<SlotHit> minionHits = new ArrayList<>();
	private List<PvTooltip.Line> hoverTip = List.of();

	public void apply(CollectionSnapshot snapshot) {
		this.snapshot = snapshot == null ? CollectionSnapshot.empty() : snapshot;
		this.selectedCat = 0;
		this.selectedItem = 0;
		this.selectedMinion = this.snapshot.minions().isEmpty() ? -1 : 0;
		this.gridScroll = 0;
		this.minionScroll = 0;
		this.tierScroll = 0;
		this.hoverTip = List.of();
		prefetchIcons();
	}

	public void render(
		GuiGraphicsExtractor g,
		Font font,
		PvSubTab sub,
		int x,
		int y,
		int w,
		int h,
		int mouseX,
		int mouseY,
		int screenW,
		int screenH
	) {
		this.itemHits.clear();
		this.minionHits.clear();
		this.hoverTip = List.of();
		if (sub == PvSubTab.COLLECTIONS_MINIONS) {
			renderMinions(g, font, x, y, w, h, mouseX, mouseY);
		} else {
			renderCollections(g, font, x, y, w, h, mouseX, mouseY);
			if (!this.hoverTip.isEmpty()) {
				PvTooltip.drawStyled(g, font, this.hoverTip, mouseX, mouseY, screenW, screenH);
			}
		}
	}

	public boolean mouseClicked(double mx, double my, PvSubTab sub) {
		if (sub == PvSubTab.COLLECTIONS_MINIONS) {
			for (SlotHit hit : this.minionHits) {
				if (mx >= hit.x && mx < hit.x + hit.w && my >= hit.y && my < hit.y + hit.h) {
					this.selectedMinion = hit.index;
					this.tierScroll = 0;
					return true;
				}
			}
			return false;
		}
		for (ItemHit hit : this.itemHits) {
			if (mx >= hit.x && mx < hit.x + hit.w && my >= hit.y && my < hit.y + hit.h) {
				this.selectedCat = hit.cat;
				this.selectedItem = hit.item;
				return true;
			}
		}
		return false;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY, PvSubTab sub) {
		int step = SLOT + SLOT_GAP;
		if (sub == PvSubTab.COLLECTIONS_MINIONS) {
			if (mouseY >= this.minionTop && mouseY < this.minionTop + this.minionH) {
				return scrollBy(scrollY, this.minionScroll, this.minionMaxScroll, step, v -> this.minionScroll = v);
			}
			if (mouseY >= this.tierListTop && mouseY < this.tierListTop + this.tierListH) {
				return scrollBy(scrollY, this.tierScroll, this.tierMaxScroll, 12, v -> this.tierScroll = v);
			}
			return false;
		}
		return false;
	}

	private void renderCollections(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
		PvDraw.innerPanel(g, x, y, w, h);

		List<HypixelCollectionsCache.Category> categories = this.snapshot.categories();
		if (categories.isEmpty()) {
			PvDraw.textCentered(
				g, font,
				Component.translatable("betterpv.collections.loading").getString(),
				x + w / 2, y + h / 2, PvDraw.COLOR_MUTED
			);
			return;
		}
		clampSelection(categories);

		this.gridTop = y + PAD;
		this.gridH = Math.max(20, h - PAD * 2);
		drawAllCategories(g, font, categories, x + PAD, this.gridTop, w - PAD * 2, this.gridH, mouseX, mouseY);
	}

	private void drawAllCategories(
		GuiGraphicsExtractor g,
		Font font,
		List<HypixelCollectionsCache.Category> categories,
		int x,
		int y,
		int w,
		int h,
		int mouseX,
		int mouseY
	) {
		int n = categories.size();
		if (n <= 0) {
			return;
		}

		BoardLayout layout = layoutBoard(categories, w, h);
		int slot = layout.slot;
		int icon = layout.icon;
		int gap = layout.gap;
		int cellH = layout.cellH();
		int header = slot;
		int headerH = header + layout.rowGap;
		int rowPitch = cellH + layout.rowGap;
		int contentH = headerH + layout.maxRows * cellH + Math.max(0, layout.maxRows - 1) * layout.rowGap;

		// No scrolling - board is fitted to the panel.
		this.gridMaxScroll = 0;
		this.gridScroll = 0;

		int startX = x + Math.max(0, (w - layout.neededW) / 2);
		int startY = y + Math.max(0, (h - contentH) / 2);
		float labelScale = icon >= 14 ? 1.0f : icon >= 12 ? 0.8f : 0.7f;

		g.enableScissor(x, y, x + w, y + h);
		int cursorX = startX;
		for (int c = 0; c < n; c++) {
			HypixelCollectionsCache.Category category = categories.get(c);
			List<HypixelCollectionsCache.Item> items = category.items();
			int cols = layout.catCols[c];
			int blockW = cols * slot + Math.max(0, cols - 1) * gap;
			int headerX = cursorX + Math.max(0, (blockW - header) / 2);

			boolean catSelected = c == this.selectedCat;
			PvDraw.fill(g, headerX, startY, header, header, catSelected ? 0xFF2A3A55 : 0xFF16161E);
			g.outline(headerX, startY, header, header, catSelected ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_BORDER);
			ItemStack headerIcon = CollectionCategoryIcons.icon(category.id());
			if (!headerIcon.isEmpty()) {
				drawVanillaIcon(g, headerIcon, headerX + (header - icon) / 2, startY + (header - icon) / 2, icon);
			}

			for (int i = 0; i < items.size(); i++) {
				int col = i % cols;
				int row = i / cols;
				int sx = cursorX + col * (slot + gap);
				int sy = startY + headerH + row * rowPitch;
				HypixelCollectionsCache.Item item = items.get(i);
				int tier = this.snapshot.displayTier(item);
				boolean selected = c == this.selectedCat && i == this.selectedItem;
				boolean hover = mouseX >= sx && mouseX < sx + slot && mouseY >= sy && mouseY < sy + cellH;
				PvDraw.fill(g, sx, sy, slot, slot, selected ? 0xFF2A3A55 : hover ? 0xFF222230 : 0xFF101018);
				g.outline(sx, sy, slot, slot, selected ? PvDraw.COLOR_ACCENT : hover ? 0xFF4A4A5A : 0xFF2A2A35);
				drawIcon(g, resolveIconId(item.id()), sx + (slot - icon) / 2, sy + (slot - icon) / 2, icon);
				String label = String.valueOf(tier);
				int labelColor = tier > 0 ? PvDraw.COLOR_TEXT : PvDraw.COLOR_MUTED;
				int labelY = sy + slot + layout.labelGap;
				if (labelScale >= 0.99f) {
					PvDraw.textCentered(g, font, label, sx + slot / 2, labelY, labelColor);
				} else {
					int tw = Math.max(1, Math.round(font.width(label) * labelScale));
					PvDraw.textScaled(g, font, label, sx + (slot - tw) / 2, labelY, labelColor, labelScale);
				}
				this.itemHits.add(new ItemHit(sx, sy, slot, cellH, c, i));
				if (hover) {
					this.hoverTip = collectionHover(item);
				}
			}

			cursorX += blockW;
			if (c < n - 1) {
				int lineX = cursorX + layout.catSep / 2;
				PvDraw.fill(g, lineX, startY, 1, contentH, PvDraw.COLOR_BORDER);
				cursorX += layout.catSep;
			}
		}
		g.disableScissor();
	}

	private List<PvTooltip.Line> collectionHover(HypixelCollectionsCache.Item item) {
		List<PvTooltip.Line> lines = new ArrayList<>();
		long amount = this.snapshot.progressAmount(item);
		long yours = this.snapshot.viewedAmount(item.id());
		int tier = this.snapshot.displayTier(item);
		boolean maxed = tier >= item.maxTiers() && item.maxTiers() > 0;
		boolean boss = BossCollections.isBossId(item.id());

		lines.add(PvTooltip.Line.bold(item.name(), PvDraw.COLOR_GOLD));
		lines.add(PvTooltip.Line.blank());
		if (boss) {
			lines.add(new PvTooltip.Line(List.of(
				PvTooltip.Span.of("Kills: ", PvDraw.COLOR_MUTED),
				PvTooltip.Span.bold(FormatUtil.shortXp(amount), PvDraw.COLOR_TEXT)
			)));
		} else {
			lines.add(new PvTooltip.Line(List.of(
				PvTooltip.Span.of("Coop total: ", PvDraw.COLOR_MUTED),
				PvTooltip.Span.bold(FormatUtil.shortXp(amount), PvDraw.COLOR_GOLD)
			)));
			if (this.snapshot.members().size() > 1) {
				lines.add(new PvTooltip.Line(List.of(
					PvTooltip.Span.of("You: ", PvDraw.COLOR_MUTED),
					PvTooltip.Span.bold(FormatUtil.shortXp(yours), PvDraw.COLOR_ACCENT)
				)));
			}
		}
		lines.add(PvTooltip.Line.blank());
		lines.add(new PvTooltip.Line(List.of(
			PvTooltip.Span.of("Tier: ", PvDraw.COLOR_MUTED),
			PvTooltip.Span.bold(
				tier + " / " + item.maxTiers(),
				maxed ? MINION_UNLOCKED : tier > 0 ? PvDraw.COLOR_TEXT : MINION_LOCKED
			)
		)));
		HypixelCollectionsCache.Tier next = item.nextTier(amount);
		if (maxed || next == null) {
			lines.add(PvTooltip.Line.bold("Maxed", MINION_UNLOCKED));
		} else {
			int pct = Math.round(item.progressToNext(amount) * 100f);
			lines.add(new PvTooltip.Line(List.of(
				PvTooltip.Span.of("Progress: ", PvDraw.COLOR_MUTED),
				PvTooltip.Span.of(
					FormatUtil.shortXp(amount) + " / " + FormatUtil.shortXp(next.amountRequired()),
					PvDraw.COLOR_TEXT
				),
				PvTooltip.Span.of(" (" + pct + "%)", PvDraw.COLOR_ACCENT)
			)));
		}
		// Shared collections only - boss kills are personal per member.
		if (!boss && this.snapshot.members().size() > 1) {
			lines.add(PvTooltip.Line.blank());
			lines.add(PvTooltip.Line.bold(
				Component.translatable("betterpv.collections.coop").getString(),
				PvDraw.COLOR_ACCENT
			));
			for (CollectionSnapshot.Member member : this.snapshot.membersByAmount(item.id())) {
				boolean viewed = member.uuid().equals(this.snapshot.viewedUuid());
				List<PvTooltip.Span> spans = new ArrayList<>(memberNameSpans(member));
				spans.add(PvTooltip.Span.of(": ", PvDraw.COLOR_MUTED));
				spans.add(PvTooltip.Span.bold(
					FormatUtil.shortXp(member.amount(item.id())),
					viewed ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_GOLD
				));
				lines.add(new PvTooltip.Line(spans));
			}
		}
		return lines;
	}

	private List<PvTooltip.Span> memberNameSpans(CollectionSnapshot.Member member) {
		String name = this.snapshot.displayName(member);
		PlayerCustomizationRegistry.PlayerCustomization custom = findCosmetics(member, name);
		if (custom != null && custom.hasExplicitNameColors()) {
			return cosmeticNameSpans(name, custom);
		}

		JsonObject rankPlayer = this.snapshot.playerRank(member.uuid());
		if (rankPlayer != null) {
			return HypixelRanks.nameSpans(name, rankPlayer);
		}

		boolean viewed = member.uuid().equals(this.snapshot.viewedUuid());
		return List.of(PvTooltip.Span.bold(name, viewed ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_TEXT));
	}

	private static PlayerCustomizationRegistry.PlayerCustomization findCosmetics(
		CollectionSnapshot.Member member,
		String name
	) {
		UUID uuid = HypixelApiClient.parseUndashedUuid(member.uuid());
		if (uuid != null) {
			PlayerCustomizationRegistry.PlayerCustomization byUuid =
				PlayerCustomizationRegistry.find(new GameProfile(uuid, name == null ? "" : name));
			if (byUuid != null) {
				return byUuid;
			}
		}
		return PlayerCustomizationRegistry.findByName(name);
	}

	/** Build tooltip spans from cosmetics colours directly (don't rely on Component visit). */
	private static List<PvTooltip.Span> cosmeticNameSpans(
		String name,
		PlayerCustomizationRegistry.PlayerCustomization custom
	) {
		String content = custom.displayName(name == null ? "" : name);
		int[] codePoints = content.codePoints().toArray();
		List<PvTooltip.Span> spans = new ArrayList<>(codePoints.length + 2);
		boolean bold = custom.nameBold();
		List<Integer> letters = custom.nameLetterColors();
		PlayerCustomizationRegistry.NameColors colors = custom.nameColors();

		if (custom.hasRankPrefix() && custom.nameRankPrefix().label() != null) {
			String label = custom.nameRankPrefix().label();
			int prefixColor = colors != null ? colors.left() : PvDraw.COLOR_ACCENT;
			if (custom.nameRankPrefix().colors() != null) {
				prefixColor = custom.nameRankPrefix().colors().left();
			}
			spans.add(new PvTooltip.Span(label + " ", 0xFF000000 | (prefixColor & 0xFFFFFF), bold));
		}

		if (codePoints.length == 0) {
			return spans.isEmpty() ? List.of(PvTooltip.Span.bold(content, PvDraw.COLOR_TEXT)) : spans;
		}

		int fallbackLetter = letters.isEmpty() ? 0xFFFFFF : letters.get(letters.size() - 1);
		for (int i = 0; i < codePoints.length; i++) {
			int rgb;
			if (!letters.isEmpty()) {
				rgb = i < letters.size() ? letters.get(i) : fallbackLetter;
			} else if (colors != null) {
				float progress = codePoints.length <= 1 ? 0f : (float) i / (float) (codePoints.length - 1);
				float spacing = Math.max(1f, Math.min(10f, colors.spacing()));
				float loop = (progress * spacing) % 1f;
				if (loop < 0f) {
					loop += 1f;
				}
				float t = loop <= 0.5f ? loop * 2f : (1f - loop) * 2f;
				rgb = lerpRgb(colors.left(), colors.right(), t);
			} else {
				rgb = PvDraw.COLOR_TEXT;
			}
			spans.add(new PvTooltip.Span(
				new String(codePoints, i, 1),
				0xFF000000 | (rgb & 0xFFFFFF),
				bold
			));
		}

		if (custom.hasBadge() && custom.nameBadge() != null) {
			spans.add(PvTooltip.Span.of(" ", PvDraw.COLOR_MUTED));
			spans.add(new PvTooltip.Span(
				custom.nameBadge().label(),
				0xFF000000 | (custom.nameBadge().color() & 0xFFFFFF),
				custom.nameBadge().bold()
			));
		}
		return spans;
	}

	private static int lerpRgb(int left, int right, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int lr = (left >> 16) & 0xFF;
		int lg = (left >> 8) & 0xFF;
		int lb = left & 0xFF;
		int rr = (right >> 16) & 0xFF;
		int rg = (right >> 8) & 0xFF;
		int rb = right & 0xFF;
		int r = Math.round(lr + (rr - lr) * t);
		int g = Math.round(lg + (rg - lg) * t);
		int b = Math.round(lb + (rb - lb) * t);
		return (r << 16) | (g << 8) | b;
	}

	private static BoardLayout layoutBoard(List<HypixelCollectionsCache.Category> categories, int w, int h) {
		BoardLayout best = null;
		for (int icon : COLL_ICON_STEPS) {
			// Comfortable metrics first for this icon size, then tighter if needed.
			int[][] packs = icon >= 14
				? new int[][] {{4, 5, 4, 2, 9, 8}, {2, 4, 3, 2, 8, 6}, {2, 3, 2, 1, 8, 6}}
				: new int[][] {{2, 4, 3, 1, 8, 6}, {2, 3, 2, 1, 7, 5}, {2, 2, 2, 1, 7, 4}};
			for (int[] pack : packs) {
				int slotPad = pack[0];
				int gap = pack[1];
				int rowGap = pack[2];
				int labelGap = pack[3];
				int labelH = pack[4];
				int catSep = pack[5];
				int slot = icon + slotPad;
				BoardLayout candidate = packBoard(categories, w, h, icon, slot, gap, rowGap, labelGap, labelH, catSep);
				if (candidate.neededW <= w && candidate.contentH() <= h) {
					return candidate;
				}
				best = candidate;
			}
		}
		return best;
	}

	/** Build column counts for a fixed cell size; prefer fitting height without scroll. */
	private static BoardLayout packBoard(
		List<HypixelCollectionsCache.Category> categories,
		int w,
		int h,
		int icon,
		int slot,
		int gap,
		int rowGap,
		int labelGap,
		int labelH,
		int catSep
	) {
		int n = categories.size();
		int cellH = slot + labelGap + labelH;
		int headerH = slot + rowGap;
		int availRows = Math.max(1, (h - headerH + rowGap) / (cellH + rowGap));

		int[] catCols = new int[n];
		for (int c = 0; c < n; c++) {
			int items = categories.get(c).items().size();
			catCols[c] = Math.max(1, (items + availRows - 1) / availRows);
		}

		while (boardWidth(catCols, slot, gap, catSep) > w) {
			int best = -1;
			int bestCols = 1;
			for (int c = 0; c < n; c++) {
				if (catCols[c] > bestCols) {
					bestCols = catCols[c];
					best = c;
				}
			}
			if (best < 0 || bestCols <= 1) {
				break;
			}
			catCols[best]--;
		}

		boolean grew = true;
		while (grew) {
			grew = false;
			if (w - boardWidth(catCols, slot, gap, catSep) < slot + gap) {
				break;
			}
			int pick = -1;
			int pickRows = 0;
			for (int c = 0; c < n; c++) {
				int items = categories.get(c).items().size();
				int cols = catCols[c];
				int rows = (items + cols - 1) / cols;
				int newRows = (items + cols) / (cols + 1);
				if (newRows < rows && rows > pickRows) {
					pickRows = rows;
					pick = c;
				}
			}
			if (pick < 0) {
				break;
			}
			catCols[pick]++;
			if (boardWidth(catCols, slot, gap, catSep) > w) {
				catCols[pick]--;
				break;
			}
			grew = true;
		}

		int maxRows = 1;
		for (int c = 0; c < n; c++) {
			int items = categories.get(c).items().size();
			maxRows = Math.max(maxRows, (items + catCols[c] - 1) / catCols[c]);
		}
		return new BoardLayout(icon, slot, gap, rowGap, labelGap, labelH, catSep, catCols, boardWidth(catCols, slot, gap, catSep), maxRows);
	}

	private static int boardWidth(int[] catCols, int slot, int gap, int sep) {
		int neededW = 0;
		for (int c = 0; c < catCols.length; c++) {
			int cols = catCols[c];
			neededW += cols * slot + Math.max(0, cols - 1) * gap;
			if (c < catCols.length - 1) {
				neededW += sep;
			}
		}
		return neededW;
	}

	private static void drawVanillaIcon(GuiGraphicsExtractor g, ItemStack icon, int x, int y, int size) {
		if (icon == null || icon.isEmpty()) {
			return;
		}
		int draw = Math.max(1, Math.min(ITEM_ICON, size));
		if (draw == ITEM_ICON) {
			g.item(icon, x, y);
			return;
		}
		float scale = draw / (float) ITEM_ICON;
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.item(icon, 0, 0);
		g.pose().popMatrix();
	}

	private record BoardLayout(
		int icon,
		int slot,
		int gap,
		int rowGap,
		int labelGap,
		int labelH,
		int catSep,
		int[] catCols,
		int neededW,
		int maxRows
	) {
		int cellH() {
			return slot + labelGap + labelH;
		}

		int contentH() {
			int headerH = slot + rowGap;
			return headerH + maxRows * cellH() + Math.max(0, maxRows - 1) * rowGap;
		}
	}

	private void renderMinions(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
		int rightW = Math.max(96, Math.min(118, (int) Math.round(w * 0.24)));
		int leftW = w - rightW - GAP;
		int rightX = x + leftW + GAP;

		PvDraw.innerPanel(g, x, y, leftW, h);
		PvDraw.innerPanel(g, rightX, y, rightW, h);

		List<CollectionSnapshot.MinionEntry> minions = this.snapshot.minions();
		PvDraw.text(g, font, Component.translatable("betterpv.collections.minions").getString(), x + PAD, y + PAD, PvDraw.COLOR_TEXT);
		this.minionTop = y + PAD + font.lineHeight + 4;
		this.minionH = Math.max(20, y + h - PAD - this.minionTop);

		if (minions.isEmpty()) {
			PvDraw.textCentered(
				g, font,
				Component.translatable("betterpv.collections.minions_empty").getString(),
				x + leftW / 2, y + h / 2, PvDraw.COLOR_MUTED
			);
			this.minionMaxScroll = 0;
			this.selectedMinion = -1;
			drawMinionDetail(g, font, null, rightX, y, rightW, h);
			return;
		}
		this.selectedMinion = Math.max(0, Math.min(this.selectedMinion, minions.size() - 1));

		int listX = x + PAD;
		int listW = leftW - PAD * 2;
		int count = minions.size();
		int slot = SLOT;
		int minGap = 5;
		int maxGap = 14;

		// One row shorter than a full-height pack → more columns, wider grid.
		int fitRows = Math.max(1, (this.minionH + minGap) / (slot + minGap));
		int idealRows = Math.max(1, fitRows - 1);
		int maxCols = Math.max(1, (listW + minGap) / (slot + minGap));
		int cols = Math.max(1, Math.min(maxCols, (count + idealRows - 1) / idealRows));
		int rows = (count + cols - 1) / cols;

		// Widen columns if the tall layout would clip below the panel.
		while (cols < maxCols) {
			int trialGapY = rows <= 1 ? 0 : Math.max(minGap, Math.min(maxGap, (this.minionH - rows * slot) / Math.max(1, rows - 1)));
			int trialH = rows * slot + Math.max(0, rows - 1) * trialGapY;
			if (trialH <= this.minionH) {
				break;
			}
			cols++;
			rows = (count + cols - 1) / cols;
		}

		int gapX = cols <= 1 ? 0 : Math.max(minGap, Math.min(maxGap, (listW - cols * slot) / Math.max(1, cols - 1)));
		int gapY = rows <= 1 ? 0 : Math.max(minGap, Math.min(maxGap, (this.minionH - rows * slot) / Math.max(1, rows - 1)));

		int gridW = cols * slot + Math.max(0, cols - 1) * gapX;
		int contentH = rows * slot + Math.max(0, rows - 1) * gapY;
		int startX = listX + Math.max(0, (listW - gridW) / 2);
		int startY = this.minionTop + Math.max(0, (this.minionH - contentH) / 2);
		this.minionMaxScroll = Math.max(0, contentH - this.minionH);
		this.minionScroll = Math.max(0, Math.min(this.minionScroll, this.minionMaxScroll));

		g.enableScissor(listX, this.minionTop, listX + listW, this.minionTop + this.minionH);
		for (int i = 0; i < count; i++) {
			int col = i % cols;
			int row = i / cols;
			int sx = startX + col * (slot + gapX);
			int sy = startY + row * (slot + gapY) - this.minionScroll;
			if (sy + slot < this.minionTop || sy > this.minionTop + this.minionH) {
				continue;
			}
			CollectionSnapshot.MinionEntry minion = minions.get(i);
			boolean selected = i == this.selectedMinion;
			boolean hover = mouseX >= sx && mouseX < sx + slot && mouseY >= sy && mouseY < sy + slot;
			PvDraw.fill(g, sx, sy, slot, slot, selected ? 0xFF2A3A55 : hover ? 0xFF222230 : 0xFF101018);
			g.outline(sx, sy, slot, slot, selected ? PvDraw.COLOR_ACCENT : hover ? 0xFF4A4A5A : 0xFF2A2A35);
			drawIcon(g, minion.iconId(), sx + (slot - ITEM_ICON) / 2, sy + (slot - ITEM_ICON) / 2 - 1);
			boolean maxed = minion.maxCrafted() >= minion.tierCap() && minion.tierCap() > 0;
			PvDraw.textRight(
				g, font,
				String.valueOf(minion.maxCrafted()),
				sx + slot - 1, sy + slot - font.lineHeight + 1,
				maxed ? MINION_UNLOCKED : MINION_LOCKED
			);
			this.minionHits.add(new SlotHit(sx, sy, slot, slot, i));
		}
		g.disableScissor();

		drawMinionDetail(g, font, minions.get(this.selectedMinion), rightX, y, rightW, h);
	}

	private void drawMinionDetail(
		GuiGraphicsExtractor g,
		Font font,
		CollectionSnapshot.MinionEntry minion,
		int x,
		int y,
		int w,
		int h
	) {
		int cx = x + PAD;
		int cy = y + PAD;
		int contentW = w - PAD * 2;
		if (minion == null) {
			PvDraw.textCentered(
				g, font,
				Component.translatable("betterpv.collections.select_minion").getString(),
				x + w / 2, y + h / 2, PvDraw.COLOR_MUTED
			);
			this.tierListTop = cy;
			this.tierListH = 0;
			this.tierMaxScroll = 0;
			return;
		}

		drawIcon(g, minion.iconId(), cx, cy);
		PvDraw.text(
			g, font,
			trim(font, minion.displayName(), contentW - ITEM_ICON - 6),
			cx + ITEM_ICON + 4,
			cy + (ITEM_ICON - font.lineHeight) / 2,
			PvDraw.COLOR_TEXT
		);
		cy += ITEM_ICON + 4;
		boolean maxed = minion.maxCrafted() >= minion.tierCap() && minion.tierCap() > 0;
		PvDraw.text(
			g, font,
			"T" + minion.maxCrafted() + " / " + minion.tierCap(),
			cx, cy,
			maxed ? MINION_UNLOCKED : MINION_LOCKED
		);
		cy += font.lineHeight + 2;
		PvDraw.text(
			g, font,
			Component.translatable("betterpv.collections.minions_shared").getString(),
			cx, cy,
			PvDraw.COLOR_MUTED
		);
		cy += font.lineHeight + 3;

		PvDraw.text(g, font, Component.translatable("betterpv.collections.tiers").getString(), cx, cy, PvDraw.COLOR_MUTED);
		cy += font.lineHeight + 2;
		this.tierListTop = cy;
		this.tierListH = Math.max(20, y + h - PAD - this.tierListTop);

		int tiers = Math.max(1, minion.tierCap());
		int naturalRow = font.lineHeight + 1;
		int fitRow = Math.max(8, this.tierListH / tiers);
		int rowH = Math.min(naturalRow, fitRow);
		float tierScale = rowH < font.lineHeight ? rowH / (float) font.lineHeight : 1.0f;
		this.tierMaxScroll = Math.max(0, tiers * rowH - this.tierListH);
		this.tierScroll = Math.max(0, Math.min(this.tierScroll, this.tierMaxScroll));

		g.enableScissor(cx, this.tierListTop, cx + contentW, this.tierListTop + this.tierListH);
		int rowY = this.tierListTop - this.tierScroll;
		for (int t = 1; t <= tiers; t++) {
			if (rowY + rowH >= this.tierListTop && rowY <= this.tierListTop + this.tierListH) {
				boolean crafted = minion.crafted(t);
				int color = crafted ? MINION_UNLOCKED : MINION_LOCKED;
				String label = "T" + t;
				if (tierScale >= 0.99f) {
					PvDraw.text(g, font, label, cx, rowY, color);
					Component mark = PvDraw.styled(crafted ? MINION_TICK : MINION_CROSS, color, true);
					PvDraw.text(g, font, mark, cx + contentW - PvDraw.width(font, mark), rowY);
				} else {
					PvDraw.textScaled(g, font, label, cx, rowY, color, tierScale);
					String mark = crafted ? MINION_TICK : MINION_CROSS;
					int tw = Math.max(1, Math.round(PvDraw.widthBold(font, mark) * tierScale));
					PvDraw.textScaled(g, font, mark, cx + contentW - tw, rowY, color, tierScale);
				}
			}
			rowY += rowH;
		}
		g.disableScissor();
	}

	private void clampSelection(List<HypixelCollectionsCache.Category> categories) {
		this.selectedCat = Math.max(0, Math.min(this.selectedCat, categories.size() - 1));
		List<HypixelCollectionsCache.Item> items = categories.get(this.selectedCat).items();
		if (items.isEmpty()) {
			this.selectedItem = 0;
			return;
		}
		this.selectedItem = Math.max(0, Math.min(this.selectedItem, items.size() - 1));
	}

	private static String resolveIconId(String collectionId) {
		if (BossCollections.isBossId(collectionId)) {
			for (BossCollections.BossDef boss : BossCollections.bosses()) {
				if (boss.id().equalsIgnoreCase(collectionId)) {
					return boss.iconId();
				}
			}
		}
		List<String> keys = new ArrayList<>();
		keys.add(CollectionIds.iconId(collectionId));
		keys.addAll(CollectionIds.lookupKeys(collectionId));
		String fallback = CollectionIds.iconId(collectionId);
		for (String key : keys) {
			if (key == null || key.isBlank()) {
				continue;
			}
			if (SkyBlockItemFactory.customIcon(key) != null) {
				return key;
			}
			ItemStack stack = SkyBlockItemFactory.iconStack(key);
			if (!stack.isEmpty() && !stack.is(Items.PAPER)) {
				return key;
			}
		}
		return fallback;
	}

	private void drawIcon(GuiGraphicsExtractor g, String id, int x, int y, int size) {
		int draw = Math.min(ITEM_ICON, Math.max(1, size));
		Identifier texture = SkyBlockItemFactory.customIcon(id);
		ItemStack icon = SkyBlockItemFactory.iconStack(id);
		if (texture != null) {
			int tex = SkyBlockItemFactory.customIconSize(id);
			g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, draw, draw, tex, tex, tex, tex);
		} else if (!icon.isEmpty()) {
			drawVanillaIcon(g, icon, x, y, draw);
		}
	}

	private void drawIcon(GuiGraphicsExtractor g, String id, int x, int y) {
		drawIcon(g, id, x, y, ITEM_ICON);
	}

	private void prefetchIcons() {
		List<String> ids = new ArrayList<>();
		for (HypixelCollectionsCache.Category category : this.snapshot.categories()) {
			for (HypixelCollectionsCache.Item item : category.items()) {
				ids.add(resolveIconId(item.id()));
				ids.add(CollectionIds.iconId(item.id()));
				ids.addAll(CollectionIds.lookupKeys(item.id()));
			}
		}
		for (BossCollections.BossDef boss : BossCollections.bosses()) {
			ids.add(boss.iconId());
		}
		for (CollectionSnapshot.MinionEntry minion : this.snapshot.minions()) {
			ids.add(minion.iconId());
			ids.add(minion.id() + "_1");
		}
		SkyBlockItemFactory.prefetchIds(ids);
	}

	private static boolean scrollBy(double scrollY, int scroll, int maxScroll, int step, java.util.function.IntConsumer setter) {
		if (maxScroll <= 0) {
			return false;
		}
		int before = scroll;
		int next = scroll;
		if (scrollY > 0) {
			next = Math.max(0, scroll - step);
		} else if (scrollY < 0) {
			next = Math.min(maxScroll, scroll + step);
		}
		setter.accept(next);
		return next != before;
	}

	private static String trim(Font font, String value, int maxW) {
		if (value == null) {
			return "";
		}
		if (maxW <= 0 || font.width(value) <= maxW) {
			return value;
		}
		String ellipsis = "...";
		int ellipsisW = font.width(ellipsis);
		if (maxW <= ellipsisW) {
			return ellipsis;
		}
		StringBuilder sb = new StringBuilder(value);
		while (sb.length() > 0 && font.width(sb.toString()) + ellipsisW > maxW) {
			sb.setLength(sb.length() - 1);
		}
		return sb + ellipsis;
	}

	private record ItemHit(int x, int y, int w, int h, int cat, int item) {
	}

	private record SlotHit(int x, int y, int w, int h, int index) {
	}
}
